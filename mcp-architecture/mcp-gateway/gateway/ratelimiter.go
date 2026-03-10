package gateway

import (
	"sync"
	"time"
)

// CircuitState 熔断器状态
type CircuitState int

const (
	CircuitClosed   CircuitState = iota // 正常
	CircuitOpen                         // 熔断
	CircuitHalfOpen                     // 半开（试探）
)

// CircuitBreaker 简单熔断器
type CircuitBreaker struct {
	mu           sync.Mutex
	state        CircuitState
	failCount    int
	threshold    int           // 连续失败多少次触发熔断
	resetTimeout time.Duration // 熔断后多久进入半开
	lastFailTime time.Time
}

func NewCircuitBreaker(threshold int, resetTimeout time.Duration) *CircuitBreaker {
	return &CircuitBreaker{
		state:        CircuitClosed,
		threshold:    threshold,
		resetTimeout: resetTimeout,
	}
}

func (cb *CircuitBreaker) Allow() bool {
	cb.mu.Lock()
	defer cb.mu.Unlock()

	switch cb.state {
	case CircuitClosed:
		return true
	case CircuitOpen:
		if time.Since(cb.lastFailTime) > cb.resetTimeout {
			cb.state = CircuitHalfOpen
			return true
		}
		return false
	case CircuitHalfOpen:
		return true
	}
	return false
}

func (cb *CircuitBreaker) RecordSuccess() {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	cb.failCount = 0
	cb.state = CircuitClosed
}

func (cb *CircuitBreaker) RecordFailure() {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	cb.failCount++
	cb.lastFailTime = time.Now()
	if cb.failCount >= cb.threshold {
		cb.state = CircuitOpen
	}
}

// SimpleRateLimiter 简单令牌桶限流器
type SimpleRateLimiter struct {
	mu       sync.Mutex
	tokens   map[string]int       // userID -> remaining tokens
	limits   int                  // 每周期令牌数
	lastReset map[string]time.Time
	period   time.Duration
}

func NewSimpleRateLimiter(limit int, period time.Duration) *SimpleRateLimiter {
	return &SimpleRateLimiter{
		tokens:    make(map[string]int),
		limits:    limit,
		lastReset: make(map[string]time.Time),
		period:    period,
	}
}

func (rl *SimpleRateLimiter) Allow(userID string) bool {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	now := time.Now()
	lastReset, ok := rl.lastReset[userID]
	if !ok || now.Sub(lastReset) > rl.period {
		rl.tokens[userID] = rl.limits
		rl.lastReset[userID] = now
	}

	if rl.tokens[userID] > 0 {
		rl.tokens[userID]--
		return true
	}
	return false
}
