# 字段配置体系设计文档

## 一、现状问题分析

### 1.1 无用配置字段（Orphaned Config）

`application.yml` 在 `mcp.db.columns.testcase` 下定义了以下字段，但 `TestCaseService` 中从未通过 `db.testcaseCol()` 引用它们：

```yaml
testcase:
  caseType: case_type       # ← 无引用
  priority: priority        # ← 无引用
  precondition: precondition # ← 无引用
  steps: steps              # ← 无引用
  expectedResult: expected_result # ← 无引用
  status: status            # ← 无引用
  createdAt: created_at     # ← 无引用
```

**根因**：重构后 `searchTestCase` 改用 `SELECT tc.*`，不再逐字段引用。
这些配置成了死代码——存在但不生效，字段名改了也不会反映到查询中。

### 1.2 `SELECT *` 的问题

使用 `tc.*` 的副作用：
- H2 返回全大写列名（`CASE_NAME`），MySQL 返回原始列名（`case_name`）
- AI 看到的字段名随数据库方言不同而变化，不稳定
- 多余的隐藏字段（如外键 `project_id`）也被返回，浪费 context token
- 用户额外添加的业务字段无法控制是否返回

### 1.3 缺少字段语义定义

当前配置只有「逻辑名 → 实际列名」的映射，没有：
- 字段的中文含义（AI 看到 `priority` 知道是优先级，但看到 `custom_tag1` 就蒙了）
- 字段的值域说明（如 `priority` 的取值是 P0/P1/P2/P3，P0 最高）
- 字段是否可作为搜索条件

### 1.4 场景适配困难

不同业务方数据库可能：
- 用不同表名（`test_case` vs `tc_case` vs `qa_case`）
- 用不同字段名（`case_name` vs `title` vs `case_title`）
- 有额外字段（`module`, `automation_flag`, `business_domain`）
- 某些字段在特定场景下无意义，不应返回

当前设计只能通过 Spring Profile 切换整套配置，无法精细控制单个字段。

---

## 二、新设计：富字段配置（Rich Field Config）

### 2.1 核心概念

将字段配置从简单的「名称映射」升级为「字段定义」：

```
字段定义 = 实际列名 + 显示名 + 语义描述 + 是否输出 + 是否可搜索
```

### 2.2 配置结构

```yaml
mcp:
  db:
    tables:
      testcase:                           # 逻辑表名（代码中引用）
        name: t_test_case                 # 实际 DB 表名
        label: 测试用例                    # 中文名（文档用）
        fields:
          id:                             # 逻辑字段名（代码中引用、SQL 别名）
            column: id                    # 实际 DB 列名
            label: 用例ID                 # 中文名（文档用）
            visible: false                # false = 不出现在查询结果中
          caseName:
            column: case_name
            label: 用例名称
            searchable: true              # true = 支持 LIKE 模糊搜索
          priority:
            column: priority
            label: 优先级
            description: "P0最高，P1次之，P2普通，P3最低"  # 值域说明
          customTag:                      # 业务方新增的动态字段
            column: custom_tag
            label: 自定义标签
            visible: true                 # 新加字段，自动进入查询结果
```

### 2.3 字段配置说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `column` | String | 必填 | 实际 DB 列名 |
| `label` | String | 同逻辑名 | 中文显示名，文档和 AI 工具描述用 |
| `description` | String | - | 字段含义、值域说明，写入 MCP 工具描述 |
| `visible` | boolean | true | 是否出现在 SELECT 结果中 |
| `searchable` | boolean | false | 是否支持作为 WHERE 过滤条件（扩展用） |

### 2.4 SELECT 构建逻辑

```
SELECT
  tc.{column} AS {logicalName},   ← 对所有 visible=true 的字段
  tc.{column} AS {logicalName},
  ...
  p.{nameCol} AS projectName       ← JOIN 带入的字段，固定命名
FROM t_test_case tc
JOIN t_project p ON ...
```

效果：
- 结果键名固定为逻辑名（camelCase），与 DB 方言无关
- 通过 `visible: false` 隐藏 `id`、`projectId` 等内部字段
- 动态字段直接加 YAML 条目即可生效，无需改 Java 代码

---

## 三、动态字段扩展场景

### 场景 A：业务方新增辅助字段

数据库新增 `automation_flag` 和 `module_name` 字段，只需在 YAML 中追加：

```yaml
fields:
  automationFlag:
    column: automation_flag
    label: 自动化标志
    description: "Y=已自动化，N=手工用例"
  moduleName:
    column: module_name
    label: 所属模块
    searchable: true   # 允许按模块名搜索
```

**不需要修改任何 Java 代码**。

### 场景 B：适配不同表结构的业务方

公司 A 的测试用例表：`qa_case`，用例名字段：`title`，没有 `precondition` 字段。

创建 `application-company-a.yml`：
```yaml
mcp:
  db:
    tables:
      testcase:
        name: qa_case
        fields:
          caseName:
            column: title       # 字段名不同
          precondition:
            visible: false      # 该表没有此字段，隐藏
```

启动时指定 Profile：
```bash
java -jar mcp-server-remote.jar --spring.profiles.active=company-a
```

### 场景 C：不同场景隐藏敏感字段

测试详情场景（steps、precondition 完整返回）vs 摘要场景（只返回 caseName、priority）：

通过 Profile 切换，或将不需要的字段设为 `visible: false`。

---

## 四、字段语义对 AI 的价值

字段的 `label` 和 `description` 最终会被写入 MCP 工具描述。示例：

```
Tool: search_test_case
返回字段说明：
- caseName（用例名称）
- caseType（用例类型：功能测试/异常测试/性能测试）
- priority（优先级：P0最高，P3最低）
- steps（操作步骤）
- expectedResult（预期结果）
```

Claude 看到这个描述，就能正确理解"给我找 P0 优先级的用例"时应该怎么过滤和展示结果。

---

## 五、架构变化对比

```
旧设计：
  mcp.db.tables  → Map<String, String>  （表名）
  mcp.db.columns → Map<String, Map<String, String>>  （字段名）
  SELECT tc.*  → H2 返回 UPPER_CASE，字段不可控

新设计：
  mcp.db.tables → Map<String, TableConfig>
    TableConfig.name   → 实际表名
    TableConfig.fields → Map<String, FieldConfig>  （有序）
      FieldConfig.column      → 实际列名
      FieldConfig.label       → 中文名
      FieldConfig.description → 语义说明
      FieldConfig.visible     → 是否输出
      FieldConfig.searchable  → 是否可搜索
  SELECT tc.col AS logicalName  → 键名固定为逻辑名，跨 DB 一致
```
