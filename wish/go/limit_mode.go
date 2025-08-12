package wish

import (
	"fmt"
	"strconv"
	"strings"
	"time"
)

// LimitModeManager 限制模式管理器
type LimitModeManager struct {
	storage Storage
}

// NewLimitModeManager 创建限制模式管理器
func NewLimitModeManager(storage Storage) *LimitModeManager {
	return &LimitModeManager{
		storage: storage,
	}
}

// CheckPoolDuration 检查祈愿池是否在有效期内
func (lm *LimitModeManager) CheckPoolDuration(config WishPoolConfig) error {
	if config.Duration == nil {
		return nil // 没有持续时间限制
	}

	now := time.Now()

	// 解析开始时间
	startTime, err := time.Parse(time.RFC3339, config.Duration.StartDate)
	if err != nil {
		return fmt.Errorf("invalid start date format: %w", err)
	}

	// 解析结束时间
	endTime, err := time.Parse(time.RFC3339, config.Duration.EndDate)
	if err != nil {
		return fmt.Errorf("invalid end date format: %w", err)
	}

	// 检查是否在有效期内
	if now.Before(startTime) {
		return fmt.Errorf("wish_pool_not_started")
	}

	if now.After(endTime) {
		return fmt.Errorf("wish_pool_expired")
	}

	return nil
}

// CheckLimitMode 检查限制模式是否允许祈愿
func (lm *LimitModeManager) CheckLimitMode(user string, poolName string, config WishPoolConfig, requestCount int) error {
	if config.LimitModes == nil {
		return nil // 没有限制模式
	}

	// 获取当前记录
	record, err := lm.storage.GetLimitModeRecord(user, poolName)
	if err != nil {
		return fmt.Errorf("failed to get limit mode record: %w", err)
	}

	// 计算当前周期
	currentPeriodStart, currentPeriodEnd, err := lm.calculateCurrentPeriod(config.LimitModes)
	if err != nil {
		return fmt.Errorf("failed to calculate current period: %w", err)
	}

	// 检查是否需要重置周期
	if record.CurrentPeriodStart.IsZero() ||
		!record.CurrentPeriodStart.Equal(currentPeriodStart) ||
		!record.CurrentPeriodEnd.Equal(currentPeriodEnd) {
		// 新周期，重置计数
		record.CurrentPeriodStart = currentPeriodStart
		record.CurrentPeriodEnd = currentPeriodEnd
		record.UsedCount = 0
		record.LastRefreshTime = time.Now()

		// 保存更新后的记录
		if err := lm.storage.UpdateLimitModeRecord(user, poolName, record); err != nil {
			return fmt.Errorf("failed to update limit mode record: %w", err)
		}
	}

	// 检查是否超过限制
	if record.UsedCount+requestCount > config.LimitModes.Count {
		return fmt.Errorf("limit_mode_exceeded")
	}

	return nil
}

// IncrementLimitModeUsage 增加限制模式使用次数
func (lm *LimitModeManager) IncrementLimitModeUsage(user string, poolName string, config WishPoolConfig, count int) error {
	if config.LimitModes == nil {
		return nil // 没有限制模式
	}

	// 获取当前记录
	record, err := lm.storage.GetLimitModeRecord(user, poolName)
	if err != nil {
		return fmt.Errorf("failed to get limit mode record: %w", err)
	}

	// 计算当前周期
	currentPeriodStart, currentPeriodEnd, err := lm.calculateCurrentPeriod(config.LimitModes)
	if err != nil {
		return fmt.Errorf("failed to calculate current period: %w", err)
	}

	// 确保周期是最新的
	if record.CurrentPeriodStart.IsZero() ||
		!record.CurrentPeriodStart.Equal(currentPeriodStart) ||
		!record.CurrentPeriodEnd.Equal(currentPeriodEnd) {
		record.CurrentPeriodStart = currentPeriodStart
		record.CurrentPeriodEnd = currentPeriodEnd
		record.UsedCount = 0
		record.LastRefreshTime = time.Now()
	}

	// 增加使用次数
	record.UsedCount += count

	// 保存记录
	return lm.storage.UpdateLimitModeRecord(user, poolName, record)
}

// calculateCurrentPeriod 计算当前周期的开始和结束时间
func (lm *LimitModeManager) calculateCurrentPeriod(limitModes *LimitModes) (start, end time.Time, err error) {
	now := time.Now()

	// 解析时间单位
	duration, err := lm.parseDuration(limitModes.Time)
	if err != nil {
		return time.Time{}, time.Time{}, fmt.Errorf("invalid time format: %w", err)
	}

	switch {
	case strings.HasSuffix(limitModes.Time, "h"):
		// 小时模式
		if limitModes.RefreshAtTime != "" {
			// 指定时间刷新（每日）
			refreshTime, err := lm.parseTimeOfDay(limitModes.RefreshAtTime)
			if err != nil {
				return time.Time{}, time.Time{}, fmt.Errorf("invalid refresh time: %w", err)
			}

			// 计算今天的刷新时间
			todayRefresh := time.Date(now.Year(), now.Month(), now.Day(),
				refreshTime.Hour(), refreshTime.Minute(), 0, 0, now.Location())

			if now.Before(todayRefresh) {
				// 当前时间在今天刷新时间之前，周期从昨天开始
				start = todayRefresh.Add(-24 * time.Hour)
				end = todayRefresh
			} else {
				// 当前时间在今天刷新时间之后，周期从今天开始
				start = todayRefresh
				end = todayRefresh.Add(24 * time.Hour)
			}
		} else {
			// 按小时数计算
			hours := int(duration.Hours())
			startHour := (now.Hour() / hours) * hours
			start = time.Date(now.Year(), now.Month(), now.Day(), startHour, 0, 0, 0, now.Location())
			end = start.Add(duration)
		}

	case strings.HasSuffix(limitModes.Time, "d"):
		// 天模式
		if limitModes.RefreshAtTime != "" {
			refreshTime, err := lm.parseTimeOfDay(limitModes.RefreshAtTime)
			if err != nil {
				return time.Time{}, time.Time{}, fmt.Errorf("invalid refresh time: %w", err)
			}

			days := int(duration.Hours() / 24)
			todayRefresh := time.Date(now.Year(), now.Month(), now.Day(),
				refreshTime.Hour(), refreshTime.Minute(), 0, 0, now.Location())

			if now.Before(todayRefresh) {
				start = todayRefresh.Add(-time.Duration(days) * 24 * time.Hour)
				end = todayRefresh
			} else {
				start = todayRefresh
				end = todayRefresh.Add(time.Duration(days) * 24 * time.Hour)
			}
		} else {
			// 从当天00:00开始
			start = time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location())
			end = start.Add(duration)
		}

	case strings.HasSuffix(limitModes.Time, "w"):
		// 周模式
		weekday := int(now.Weekday())
		if weekday == 0 {
			weekday = 7 // 周日转换为7
		}

		refreshWeekday := limitModes.RefreshAtWeek
		if refreshWeekday == 0 {
			refreshWeekday = 1 // 默认周一
		}

		daysToRefresh := (refreshWeekday - weekday + 7) % 7
		if daysToRefresh == 0 && limitModes.RefreshAtTime != "" {
			refreshTime, err := lm.parseTimeOfDay(limitModes.RefreshAtTime)
			if err != nil {
				return time.Time{}, time.Time{}, fmt.Errorf("invalid refresh time: %w", err)
			}

			todayRefresh := time.Date(now.Year(), now.Month(), now.Day(),
				refreshTime.Hour(), refreshTime.Minute(), 0, 0, now.Location())

			if now.Before(todayRefresh) {
				daysToRefresh = 7 // 上周的刷新日
			}
		}

		refreshDay := now.Add(-time.Duration(daysToRefresh) * 24 * time.Hour)
		start = time.Date(refreshDay.Year(), refreshDay.Month(), refreshDay.Day(), 0, 0, 0, 0, refreshDay.Location())
		if limitModes.RefreshAtTime != "" {
			refreshTime, _ := lm.parseTimeOfDay(limitModes.RefreshAtTime)
			start = time.Date(refreshDay.Year(), refreshDay.Month(), refreshDay.Day(),
				refreshTime.Hour(), refreshTime.Minute(), 0, 0, refreshDay.Location())
		}
		end = start.Add(7 * 24 * time.Hour)

	case strings.HasSuffix(limitModes.Time, "m"):
		// 月模式
		refreshDay := limitModes.RefreshAtMonth
		if refreshDay == 0 {
			refreshDay = 1 // 默认每月1号
		}

		// 计算当月的刷新日
		currentMonth := time.Date(now.Year(), now.Month(), refreshDay, 0, 0, 0, 0, now.Location())
		if limitModes.RefreshAtTime != "" {
			refreshTime, _ := lm.parseTimeOfDay(limitModes.RefreshAtTime)
			currentMonth = time.Date(now.Year(), now.Month(), refreshDay,
				refreshTime.Hour(), refreshTime.Minute(), 0, 0, now.Location())
		}

		if now.Before(currentMonth) {
			// 当前时间在本月刷新日之前，周期从上月开始
			start = time.Date(now.Year(), now.Month()-1, refreshDay, currentMonth.Hour(), currentMonth.Minute(), 0, 0, now.Location())
			end = currentMonth
		} else {
			// 当前时间在本月刷新日之后，周期从本月开始
			start = currentMonth
			end = time.Date(now.Year(), now.Month()+1, refreshDay, currentMonth.Hour(), currentMonth.Minute(), 0, 0, now.Location())
		}

	default:
		return time.Time{}, time.Time{}, fmt.Errorf("unsupported time format: %s", limitModes.Time)
	}

	return start, end, nil
}

// parseDuration 解析持续时间字符串
func (lm *LimitModeManager) parseDuration(timeStr string) (time.Duration, error) {
	if strings.HasSuffix(timeStr, "h") {
		hours, err := strconv.Atoi(strings.TrimSuffix(timeStr, "h"))
		if err != nil {
			return 0, err
		}
		return time.Duration(hours) * time.Hour, nil
	}

	if strings.HasSuffix(timeStr, "d") {
		days, err := strconv.Atoi(strings.TrimSuffix(timeStr, "d"))
		if err != nil {
			return 0, err
		}
		return time.Duration(days) * 24 * time.Hour, nil
	}

	if strings.HasSuffix(timeStr, "w") {
		weeks, err := strconv.Atoi(strings.TrimSuffix(timeStr, "w"))
		if err != nil {
			return 0, err
		}
		return time.Duration(weeks) * 7 * 24 * time.Hour, nil
	}

	if strings.HasSuffix(timeStr, "m") {
		months, err := strconv.Atoi(strings.TrimSuffix(timeStr, "m"))
		if err != nil {
			return 0, err
		}
		return time.Duration(months) * 30 * 24 * time.Hour, nil // 近似值
	}

	if strings.HasSuffix(timeStr, "y") {
		years, err := strconv.Atoi(strings.TrimSuffix(timeStr, "y"))
		if err != nil {
			return 0, err
		}
		return time.Duration(years) * 365 * 24 * time.Hour, nil // 近似值
	}

	return time.ParseDuration(timeStr)
}

// parseTimeOfDay 解析一天中的时间 (HH:MM 格式)
func (lm *LimitModeManager) parseTimeOfDay(timeStr string) (time.Time, error) {
	t, err := time.Parse("15:04", timeStr)
	if err != nil {
		return time.Time{}, err
	}
	return t, nil
}
