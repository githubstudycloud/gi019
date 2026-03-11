package com.mcp.server.meta.skill;

import com.mcp.server.meta.domain.ParamConfig;
import com.mcp.server.meta.domain.ToolConfig;
import com.mcp.server.meta.repo.MetaConfigRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill 生成器 —— 根据 mcp_tool_config 动态生成 SKILL.md 文件。
 *
 * 生成目标：
 *   skills/{skillKey}/SKILL.md
 *   .claude/skills/{skillKey}/SKILL.md
 *
 * 触发时机：
 *   - Web UI 保存工具配置后调用 generate()
 *   - Admin API 手动触发
 */
@Component
public class SkillGenerator {

    private static final Logger log = LoggerFactory.getLogger(SkillGenerator.class);

    private final MetaConfigRepo repo;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${mcp.skill.output-dir:skills}")
    private String outputDir;

    public SkillGenerator(MetaConfigRepo repo) {
        this.repo = repo;
    }

    /**
     * 为指定 skillKey（通常与 MCP server key 对应）生成 SKILL.md。
     * skillKey 示例："execution"、"testcase"
     */
    public void generate(String skillKey, String skillTitle, List<ToolConfig> tools) {
        String content = buildSkillContent(skillKey, skillTitle, tools);
        writeSkillFile(outputDir + "/" + skillKey + "/SKILL.md", content);
        writeSkillFile(".claude/skills/" + skillKey + "/SKILL.md", content);
        log.info("[SkillGenerator] SKILL.md 已生成: skills/{}/SKILL.md", skillKey);
    }

    /** 从 DB 读取所有动态工具并生成 Skill */
    public void generateFromDb(String skillKey, String skillTitle) {
        List<ToolConfig> tools = repo.findAllTools(true);
        generate(skillKey, skillTitle, tools);
    }

    // ----------------------------------------------------------------
    // 内容构建
    // ----------------------------------------------------------------

    private String buildSkillContent(String skillKey, String skillTitle, List<ToolConfig> tools) {
        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(skillTitle).append(" Skill\n\n");
        sb.append("通过 curl 调用远端 MCP 服务（JSON-RPC 2.0）执行查询。\n\n");

        sb.append("## 连接信息\n\n");
        sb.append("- **MCP 地址**: `http://localhost:").append(serverPort).append("/mcp`\n");
        sb.append("- **协议**: Streamable HTTP（POST JSON-RPC 2.0）\n\n");

        sb.append("## 调用模板\n\n");
        sb.append("```bash\n");
        sb.append("curl -s -X POST http://localhost:").append(serverPort).append("/mcp \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"TOOL_NAME\",\"arguments\":{\"key\":\"value\"}}}'\n");
        sb.append("```\n\n");

        sb.append("## 引导式查询流程\n\n");

        // 按 sortOrder 排序
        List<ToolConfig> sorted = tools.stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .collect(Collectors.toList());

        for (int i = 0; i < sorted.size(); i++) {
            ToolConfig tool = sorted.get(i);
            sb.append("### ").append(i + 1).append(". `").append(tool.getToolKey()).append("`\n\n");
            sb.append("**").append(tool.getToolName()).append("**\n\n");
            sb.append(tool.getDescription()).append("\n\n");

            if (tool.getParams() != null && !tool.getParams().isEmpty()) {
                sb.append("**参数：**\n\n");
                sb.append("| 参数名 | 类型 | 必填 | 说明 |\n");
                sb.append("|--------|------|------|------|\n");
                for (ParamConfig p : tool.getParams()) {
                    sb.append("| `").append(p.getParamName()).append("` | ")
                      .append(p.getParamType()).append(" | ")
                      .append(p.isRequired() ? "✓" : "").append(" | ")
                      .append(p.getDescription() != null ? p.getDescription() : "").append(" |\n");
                }
                sb.append("\n");
            }

            // 生成示例 curl
            sb.append("**示例：**\n\n");
            sb.append("```bash\n");
            sb.append(buildCurlExample(tool));
            sb.append("```\n\n");
        }

        sb.append("## 注意事项\n\n");
        sb.append("- Windows 终端中文参数需 Unicode 转义：`中文` → `\\u4e2d\\u6587`\n");
        sb.append("- 建议按引导顺序调用，每步结果的 `_guide` 字段提示下一步操作\n");
        sb.append("- 性能保护：超大表查询会返回 `_perfWarning` 字段提示扫描行数\n");

        return sb.toString();
    }

    private String buildCurlExample(ToolConfig tool) {
        StringBuilder args = new StringBuilder("{");
        if (tool.getParams() != null) {
            List<ParamConfig> required = tool.getParams().stream()
                    .filter(ParamConfig::isRequired).collect(Collectors.toList());
            List<ParamConfig> optional = tool.getParams().stream()
                    .filter(p -> !p.isRequired()).limit(2).collect(Collectors.toList());

            List<ParamConfig> shown = required;
            shown.addAll(optional);

            for (int i = 0; i < shown.size(); i++) {
                ParamConfig p = shown.get(i);
                if (i > 0) args.append(", ");
                String exampleVal = getExampleValue(p);
                args.append("\\\"").append(p.getParamName()).append("\\\":").append(exampleVal);
            }
        }
        args.append("}");

        return "curl -s -X POST http://localhost:" + serverPort + "/mcp \\\n" +
               "  -H 'Content-Type: application/json' \\\n" +
               "  -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"," +
               "\"params\":{\"name\":\"" + tool.getToolKey() + "\",\"arguments\":" + args + "}}'";
    }

    private String getExampleValue(ParamConfig p) {
        if (p.getDefaultValue() != null && !p.getDefaultValue().isBlank()) {
            return "\"integer".equals(p.getParamType()) ? p.getDefaultValue() : "\\\"" + p.getDefaultValue() + "\\\"";
        }
        return switch (p.getParamType()) {
            case "integer" -> "1";
            case "boolean" -> "true";
            default -> "\\\"示例值\\\"";
        };
    }

    // ----------------------------------------------------------------
    // 文件写入
    // ----------------------------------------------------------------

    private void writeSkillFile(String relativePath, String content) {
        try {
            Path path = Paths.get(relativePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            log.warn("[SkillGenerator] 写入 {} 失败: {}", relativePath, e.getMessage());
        }
    }
}
