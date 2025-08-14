package common

import (
	"fmt"
	"strconv"
	"strings"
	"time"
)

// LimitModeManager 限制模式管理器
type LimitModeManager struct {
	storage CommonStorage
}

// NewLimitModeManager 創建限制模式管理器
func NewLimitModeManager(storage CommonStorage) *LimitModeManager {
	return &LimitModeManager{
		storage: storage,
	}
}

// CheckDuration 檢查活動是否在有效期內
func (lm *LimitModeManager) CheckDuration(config *DurationConfig) error {
	if config == nil {
		return nil // 沒有持續時間限制
	}

	now := time.Now()

	// 解析開始時間
	startTime, err := time.Parse(time.RFC3339, config.StartDate)
	if err != nil {
		return fmt.Errorf("invalid start date format: %w", err)
	}

	// 解析結束時間
	endTime, err := time.Parse(time.RFC3339, config.EndDate)
	if err != nil {
		return fmt.Errorf("invalid end date format: %w", err)
	}

	// 檢查是否在有效期內
	if now.Before(startTime) {
		return fmt.Errorf("activity_not_started")
	}

	if now.After(endTime) {
		return fmt.Errorf("activity_expired")
	}

	return nil
}

// CheckLimitMode 檢查限制模式是否允許操作
func (lm *LimitModeManager) CheckLimitMode(user string, moduleName string, poolName string, config *LimitModeConfig, requestCount int) error {
	if config == nil {
		return nil // 沒有限制模式
	}

	// 獲取當前記錄
	record, err := lm.storage.GetLimitModeRecord(user, moduleName, poolName)
	if err != nil {
		return fmt.Errorf("failed to get limit mode record: %w", err)
	}

	if record == nil {
		record = &LimitModeRecord{}
	}

	// 計算當前周期
	currentPeriodStart, currentPeriodEnd, err := lm.calculateCurrentPeriod(config)
	if err != nil {
		return fmt.Errorf("failed to calculate current period: %w", err)
	}

	// 檢查是否需要重置周期
	if record.CurrentPeriodStart.IsZero() ||
		!record.CurrentPeriodStart.Equal(currentPeriodStart) ||
		!record.CurrentPeriodEnd.Equal(currentPeriodEnd) {
		// 新周期，重置計數
		record.CurrentPeriodStart = currentPeriodStart
		record.CurrentPeriodEnd = currentPeriodEnd
		record.UsedCount = 0
		record.LastRefreshTime = time.Now()

		// 保存更新後的記錄
		if err := lm.storage.UpdateLimitModeRecord(user, moduleName, poolName, record); err != nil {
			return fmt.Errorf("failed to update limit mode record: %w", err)
		}
	}

	// 檢查是否超過限制
	if record.UsedCount+requestCount > config.Count {
		return fmt.Errorf("limit_mode_exceeded")
	}

	return nil
}

// IncrementLimitModeUsage 增加限制模式使用次數
func (lm *LimitModeManager) IncrementLimitModeUsage(user string, moduleName string, poolName string, config *LimitModeConfig, count int) error {
	if config == nil {
		return nil // 沒有限制模式
	}

	// 獲取當前記錄
	record, err := lm.storage.GetLimitModeRecord(user, moduleName, poolName)
	if err != nil {
		return fmt.Errorf("failed to get limit mode record: %w", err)
	}

	if record == nil {
		record = &LimitModeRecord{}

		// 計算當前周期
		currentPeriodStart, currentPeriodEnd, err := lm.calculateCurrentPeriod(config)
		if err != nil {
			return fmt.Errorf("failed to calculate current period: %w", err)
		}

		record.CurrentPeriodStart = currentPeriodStart
		record.CurrentPeriodEnd = currentPeriodEnd
		record.UsedCount = 0
		record.LastRefreshTime = time.Now()
	}

	// 增加使用次數
	record.UsedCount += count
	record.LastRefreshTime = time.Now()

	// 保存記錄
	return lm.storage.UpdateLimitModeRecord(user, moduleName, poolName, record)
}

// GetRemainingCount 獲取剩餘可用次數
func (lm *LimitModeManager) GetRemainingCount(user string, moduleName string, poolName string, config *LimitModeConfig) (int, error) {
	if config == nil {
		return -1, nil // 無限制
	}

	// 獲取當前記錄
	record, err := lm.storage.GetLimitModeRecord(user, moduleName, poolName)
	if err != nil {
		return 0, fmt.Errorf("failed to get limit mode record: %w", err)
	}

	if record == nil {
		return config.Count, nil // 新用戶，返回滿額度
	}

	// 計算當前周期
	currentPeriodStart, currentPeriodEnd, err := lm.calculateCurrentPeriod(config)
	if err != nil {
		return 0, fmt.Errorf("failed to calculate current period: %w", err)
	}

	// 檢查是否需要重置周期
	if record.CurrentPeriodStart.IsZero() ||
		!record.CurrentPeriodStart.Equal(currentPeriodStart) ||
		!record.CurrentPeriodEnd.Equal(currentPeriodEnd) {
		return config.Count, nil // 新周期，返回滿額度
	}

	remaining := config.Count - record.UsedCount
	if remaining < 0 {
		remaining = 0
	}

	return remaining, nil
}

// calculateCurrentPeriod 計算當前周期的開始和結束時間
func (lm *LimitModeManager) calculateCurrentPeriod(config *LimitModeConfig) (time.Time, time.Time, error) {
	now := time.Now()

	// 解析時間間隔
	duration, err := lm.parseDuration(config.Time)
	if err != nil {
		return time.Time{}, time.Time{}, err
	}

	// 解析刷新時間
	refreshHour, refreshMinute, err := lm.parseRefreshTime(config.RefreshAtTime)
	if err != nil {
		return time.Time{}, time.Time{}, err
	}

	var periodStart, periodEnd time.Time

	switch {
	case strings.HasSuffix(config.Time, "d"): // 日周期
		// 計算今日的刷新時間
		todayRefresh := time.Date(now.Year(), now.Month(), now.Day(), refreshHour, refreshMinute, 0, 0, now.Location())

		if now.Before(todayRefresh) {
			// 還未到今日刷新時間，周期是昨天到今天
			periodStart = todayRefresh.Add(-duration)
			periodEnd = todayRefresh
		} else {
			// 已過今日刷新時間，周期是今天到明天
			periodStart = todayRefresh
			periodEnd = todayRefresh.Add(duration)
		}

	case strings.HasSuffix(config.Time, "w"): // 週周期
		// 計算本週的刷新時間
		weekday := int(now.Weekday())
		if weekday == 0 {
			weekday = 7 // 將星期日從0改為7
		}

		refreshWeekday := config.RefreshAtWeek
		if refreshWeekday == 0 {
			refreshWeekday = 1 // 默認週一
		}

		daysToRefresh := refreshWeekday - weekday
		if daysToRefresh > 0 {
			daysToRefresh -= 7 // 上週的刷新日
		}

		weekRefresh := time.Date(now.Year(), now.Month(), now.Day()+daysToRefresh, refreshHour, refreshMinute, 0, 0, now.Location())

		if now.Before(weekRefresh.Add(duration)) {
			// 當前周期內
			periodStart = weekRefresh
			periodEnd = weekRefresh.Add(duration)
		} else {
			// 需要計算下一個周期
			periodStart = weekRefresh.Add(duration)
			periodEnd = periodStart.Add(duration)
		}

	case strings.HasSuffix(config.Time, "m"): // 月周期
		refreshDay := config.RefreshAtMonth
		if refreshDay == 0 {
			refreshDay = 1 // 默認每月1號
		}

		// 計算本月的刷新時間
		monthRefresh := time.Date(now.Year(), now.Month(), refreshDay, refreshHour, refreshMinute, 0, 0, now.Location())

		if now.Before(monthRefresh) {
			// 還未到本月刷新時間，周期是上月到本月
			lastMonth := monthRefresh.AddDate(0, -1, 0)
			periodStart = lastMonth
			periodEnd = monthRefresh
		} else {
			// 已過本月刷新時間，周期是本月到下月
			periodStart = monthRefresh
			periodEnd = monthRefresh.AddDate(0, 1, 0)
		}

	default:
		// 固定時長周期（如小時）
		periodStart = now.Truncate(duration)
		periodEnd = periodStart.Add(duration)
	}

	return periodStart, periodEnd, nil
}

// parseDuration 解析持續時間字符串
func (lm *LimitModeManager) parseDuration(timeStr string) (time.Duration, error) {
	if len(timeStr) < 2 {
		return 0, fmt.Errorf("invalid time format: %s", timeStr)
	}

	numStr := timeStr[:len(timeStr)-1]
	unit := timeStr[len(timeStr)-1:]

	num, err := strconv.Atoi(numStr)
	if err != nil {
		return 0, fmt.Errorf("invalid time number: %s", numStr)
	}

	switch unit {
	case "h":
		return time.Duration(num) * time.Hour, nil
	case "d":
		return time.Duration(num) * 24 * time.Hour, nil
	case "w":
		return time.Duration(num) * 7 * 24 * time.Hour, nil
	case "m":
		return time.Duration(num) * 30 * 24 * time.Hour, nil // 近似月長度
	case "y":
		return time.Duration(num) * 365 * 24 * time.Hour, nil // 近似年長度
	default:
		return 0, fmt.Errorf("invalid time unit: %s", unit)
	}
}

// parseRefreshTime 解析刷新時間字符串
func (lm *LimitModeManager) parseRefreshTime(timeStr string) (int, int, error) {
	if timeStr == "" {
		return 0, 0, nil // 默認00:00
	}

	parts := strings.Split(timeStr, ":")
	if len(parts) != 2 {
		return 0, 0, fmt.Errorf("invalid time format: %s", timeStr)
	}

	hour, err := strconv.Atoi(parts[0])
	if err != nil {
		return 0, 0, fmt.Errorf("invalid hour: %s", parts[0])
	}

	minute, err := strconv.Atoi(parts[1])
	if err != nil {
		return 0, 0, fmt.Errorf("invalid minute: %s", parts[1])
	}

	if hour < 0 || hour > 23 || minute < 0 || minute > 59 {
		return 0, 0, fmt.Errorf("invalid time: %02d:%02d", hour, minute)
	}

	return hour, minute, nil
}

// CheckPoolDuration 檢查祈願池持續時間 (for wish module compatibility)
func (lm *LimitModeManager) CheckPoolDuration(config interface{}) error {
	// 嘗試從配置中提取 Duration 信息
	switch cfg := config.(type) {
	case interface{ GetDuration() *DurationConfig }:
		duration := cfg.GetDuration()
		return lm.CheckDuration(duration)
	}

	// 如果不支持 duration，則跳過檢查
	return nil
}

// CanPerform 檢查是否可以執行操作
func (lm *LimitModeManager) CanPerform(userID, moduleName, poolName string, count int, config *LimitModeConfig) (bool, error) {
	if config == nil {
		return true, nil // 沒有限制
	}

	// 獲取限制記錄
	record, err := lm.storage.GetLimitModeRecord(userID, moduleName, poolName)
	if err != nil {
		return false, fmt.Errorf("獲取限制記錄失敗: %w", err)
	}

	// 如果沒有記錄，創建新記錄
	if record == nil {
		record = &LimitModeRecord{
			CurrentPeriodStart: time.Time{},
			CurrentPeriodEnd:   time.Time{},
			UsedCount:          0,
			LastRefreshTime:    time.Time{},
		}
	}

	// 檢查是否需要刷新周期
	now := time.Now()
	if lm.needsRefresh(record, config, now) {
		if err := lm.refreshPeriod(record, config, now); err != nil {
			return false, fmt.Errorf("刷新周期失敗: %w", err)
		}
	}

	// 檢查是否超出限制
	if record.UsedCount+count > config.Count {
		return false, nil
	}

	return true, nil
}

// RecordUsage 記錄使用情況
func (lm *LimitModeManager) RecordUsage(userID, moduleName, poolName string, count int, config *LimitModeConfig) error {
	if config == nil {
		return nil // 沒有限制
	}

	// 獲取限制記錄
	record, err := lm.storage.GetLimitModeRecord(userID, moduleName, poolName)
	if err != nil {
		return fmt.Errorf("獲取限制記錄失敗: %w", err)
	}

	// 如果沒有記錄，創建新記錄
	if record == nil {
		record = &LimitModeRecord{
			CurrentPeriodStart: time.Time{},
			CurrentPeriodEnd:   time.Time{},
			UsedCount:          0,
			LastRefreshTime:    time.Time{},
		}
	}

	// 檢查是否需要刷新周期
	now := time.Now()
	if lm.needsRefresh(record, config, now) {
		if err := lm.refreshPeriod(record, config, now); err != nil {
			return fmt.Errorf("刷新周期失敗: %w", err)
		}
	}

	// 更新使用次數
	record.UsedCount += count
	record.LastRefreshTime = now

	// 保存記錄
	return lm.storage.UpdateLimitModeRecord(userID, moduleName, poolName, record)
}

// needsRefresh 檢查是否需要刷新周期
func (lm *LimitModeManager) needsRefresh(record *LimitModeRecord, config *LimitModeConfig, now time.Time) bool {
	// 如果從未設置過周期，需要刷新
	if record.CurrentPeriodStart.IsZero() {
		return true
	}

	// 如果當前時間超過了周期結束時間，需要刷新
	if now.After(record.CurrentPeriodEnd) {
		return true
	}

	return false
}

// refreshPeriod 刷新周期
func (lm *LimitModeManager) refreshPeriod(record *LimitModeRecord, config *LimitModeConfig, now time.Time) error {
	var startTime, endTime time.Time

	switch config.Time {
	case "1d": // 每日
		startTime = lm.getStartOfDay(now, config.RefreshAtTime)
		if now.Before(startTime) {
			// 如果現在時間在今天的刷新時間之前，使用昨天的刷新時間
			startTime = startTime.AddDate(0, 0, -1)
		}
		endTime = startTime.AddDate(0, 0, 1)

	case "1w": // 每週
		startTime = lm.getStartOfWeek(now, config.RefreshAtWeek, config.RefreshAtTime)
		if now.Before(startTime) {
			// 如果現在時間在本週的刷新時間之前，使用上週的刷新時間
			startTime = startTime.AddDate(0, 0, -7)
		}
		endTime = startTime.AddDate(0, 0, 7)

	case "1m": // 每月
		startTime = lm.getStartOfMonth(now, config.RefreshAtMonth, config.RefreshAtTime)
		if now.Before(startTime) {
			// 如果現在時間在本月的刷新時間之前，使用上月的刷新時間
			startTime = startTime.AddDate(0, -1, 0)
		}
		endTime = startTime.AddDate(0, 1, 0)

	default:
		// 解析自定義時間格式，如 "2h", "30m"
		duration, err := lm.parseDuration(config.Time)
		if err != nil {
			return fmt.Errorf("無效的時間格式: %s", config.Time)
		}
		startTime = now
		endTime = now.Add(duration)
	}

	record.CurrentPeriodStart = startTime
	record.CurrentPeriodEnd = endTime
	record.UsedCount = 0

	return nil
}

// getStartOfDay 獲取指定時間的當天開始時間
func (lm *LimitModeManager) getStartOfDay(now time.Time, refreshTime string) time.Time {
	year, month, day := now.Date()
	location := now.Location()

	if refreshTime == "" {
		// 默認午夜
		return time.Date(year, month, day, 0, 0, 0, 0, location)
	}

	// 解析刷新時間
	parts := strings.Split(refreshTime, ":")
	if len(parts) != 2 {
		return time.Date(year, month, day, 0, 0, 0, 0, location)
	}

	hour, err1 := strconv.Atoi(parts[0])
	minute, err2 := strconv.Atoi(parts[1])
	if err1 != nil || err2 != nil {
		return time.Date(year, month, day, 0, 0, 0, 0, location)
	}

	return time.Date(year, month, day, hour, minute, 0, 0, location)
}

// getStartOfWeek 獲取指定週的開始時間
func (lm *LimitModeManager) getStartOfWeek(now time.Time, weekday int, refreshTime string) time.Time {
	if weekday < 1 || weekday > 7 {
		weekday = 1 // 默認週一
	}

	// 計算本週指定星期幾的日期
	currentWeekday := int(now.Weekday())
	if currentWeekday == 0 {
		currentWeekday = 7 // 將週日從0調整為7
	}

	daysToAdd := weekday - currentWeekday
	targetDay := now.AddDate(0, 0, daysToAdd)

	return lm.getStartOfDay(targetDay, refreshTime)
}

// getStartOfMonth 獲取指定月的開始時間
func (lm *LimitModeManager) getStartOfMonth(now time.Time, monthday int, refreshTime string) time.Time {
	year, month, _ := now.Date()
	location := now.Location()

	if monthday < 1 || monthday > 31 {
		monthday = 1 // 默認每月1號
	}

	// 檢查指定日期是否在當月存在
	targetDate := time.Date(year, month, monthday, 0, 0, 0, 0, location)
	if targetDate.Month() != month {
		// 如果指定日期不在當月（如2月30號），使用月底
		targetDate = time.Date(year, month+1, 0, 0, 0, 0, 0, location)
	}

	return lm.getStartOfDay(targetDate, refreshTime)
}
