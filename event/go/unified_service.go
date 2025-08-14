package event

import (
	"fmt"
	"time"

	common "neko-suite/go"
)

// EventResult 活動處理結果
type EventResult struct {
	EventID   string                 `json:"event_id"`
	EventType string                 `json:"event_type"`
	Success   bool                   `json:"success"`
	Rewards   []common.RewardConfig  `json:"rewards,omitempty"`
	Commands  []string               `json:"commands,omitempty"`
	Message   string                 `json:"message,omitempty"`
	Progress  map[string]interface{} `json:"progress,omitempty"`
	Timestamp time.Time              `json:"timestamp"`
}

// UnifiedEventService 統一的活動服務
type UnifiedEventService struct {
	config        *EventConfig
	storage       *EventStorageAdapter
	limitManager  *common.LimitModeManager
	rewardManager *common.RewardManager
}

// NewUnifiedEventService 創建統一的活動服務
func NewUnifiedEventService(config *EventConfig, storage *EventStorageAdapter) (*UnifiedEventService, error) {
	// 創建限制模式管理器
	limitManager := common.NewLimitModeManager(storage)

	// 創建獎勵管理器
	rewardManager := common.NewRewardManager()

	return &UnifiedEventService{
		config:        config,
		storage:       storage,
		limitManager:  limitManager,
		rewardManager: rewardManager,
	}, nil
}

// ProcessEvent 處理活動參與請求
func (s *UnifiedEventService) ProcessEvent(userID, eventID string) (*EventResult, error) {
	// 檢查活動是否存在
	eventConfig, exists := s.config.Events[eventID]
	if !exists {
		return nil, fmt.Errorf("活動 '%s' 不存在", eventID)
	}

	// 檢查活動時間
	if eventConfig.Duration != nil {
		if err := s.limitManager.CheckDuration(eventConfig.Duration); err != nil {
			return nil, err
		}
	}

	// 檢查限制模式
	if eventConfig.LimitModes != nil {
		canParticipate, err := s.limitManager.CanPerform(userID, "event", eventID, 1, eventConfig.LimitModes)
		if err != nil {
			return nil, fmt.Errorf("檢查限制模式失敗: %w", err)
		}
		if !canParticipate {
			return nil, fmt.Errorf("已達到參與次數限制")
		}
	}

	// 獲取用戶活動數據
	userData, err := s.storage.GetEventData(userID, eventID)
	if err != nil {
		return nil, fmt.Errorf("獲取用戶活動數據失敗: %w", err)
	}

	// 執行活動邏輯
	result, err := s.executeEventLogic(userID, eventID, eventConfig, userData)
	if err != nil {
		return nil, err
	}

	// 更新限制模式記錄
	if eventConfig.LimitModes != nil {
		if err := s.limitManager.RecordUsage(userID, "event", eventID, 1, eventConfig.LimitModes); err != nil {
			return nil, fmt.Errorf("更新限制模式記錄失敗: %w", err)
		}
	}

	// 更新用戶活動數據
	userData.TotalParticipations++
	userData.LastParticipateTime = time.Now()

	// 保存更新後的數據
	if err := s.storage.SaveEventData(userID, eventID, userData); err != nil {
		return nil, fmt.Errorf("保存用戶活動數據失敗: %w", err)
	}

	return result, nil
}

// executeEventLogic 執行具體的活動邏輯
func (s *UnifiedEventService) executeEventLogic(userID, eventID string, config *EventItemConfig, userData *UnifiedUserEventData) (*EventResult, error) {
	result := &EventResult{
		Success:  true,
		Rewards:  make([]common.RewardConfig, 0),
		Commands: make([]string, 0),
		Message:  "",
	}

	switch config.Type {
	case "daily_reward":
		return s.processDailyReward(userID, eventID, config, userData, result)
	case "check_in":
		return s.processCheckIn(userID, eventID, config, userData, result)
	case "task_complete":
		return s.processTaskComplete(userID, eventID, config, userData, result)
	case "holiday":
		return s.processHoliday(userID, eventID, config, userData, result)
	default:
		return nil, fmt.Errorf("不支持的活動類型: %s", config.Type)
	}
}

// processDailyReward 處理每日獎勵
func (s *UnifiedEventService) processDailyReward(userID, eventID string, config *EventItemConfig, userData *UnifiedUserEventData, result *EventResult) (*EventResult, error) {
	// 檢查今天是否已經領取過
	now := time.Now()
	today := now.Format("2006-01-02")

	lastDate := ""
	if !userData.LastParticipateTime.IsZero() {
		lastDate = userData.LastParticipateTime.Format("2006-01-02")
	}

	if lastDate == today {
		return nil, fmt.Errorf("今日已經領取過獎勵")
	}

	// 發放獎勵
	result.Rewards = config.Rewards
	result.Message = fmt.Sprintf("成功領取每日獎勵：%s", config.Name)

	// 如果自動發放獎勵，生成命令
	if config.AutoClaim {
		commands := s.rewardManager.BuildRewardCommand(config.Rewards, "handle_event_result")
		result.Commands = commands
	}

	return result, nil
}

// processCheckIn 處理簽到活動
func (s *UnifiedEventService) processCheckIn(userID, eventID string, config *EventItemConfig, userData *UnifiedUserEventData, result *EventResult) (*EventResult, error) {
	now := time.Now()
	today := now.Format("2006-01-02")

	lastDate := ""
	if !userData.LastParticipateTime.IsZero() {
		lastDate = userData.LastParticipateTime.Format("2006-01-02")
	}

	if lastDate == today {
		return nil, fmt.Errorf("今日已經簽到")
	}

	// 計算連續簽到天數
	yesterday := now.AddDate(0, 0, -1).Format("2006-01-02")
	if lastDate == yesterday {
		userData.ConsecutiveDays++
	} else if lastDate != "" && lastDate != today {
		userData.ConsecutiveDays = 1 // 斷簽，重新開始
	} else {
		userData.ConsecutiveDays = 1 // 首次簽到
	}

	// 根據連續簽到天數發放不同獎勵
	// 這裡簡化處理，實際可以根據 Parameters 配置更複雜的邏輯
	result.Rewards = config.Rewards
	result.Message = fmt.Sprintf("簽到成功！連續簽到 %d 天", userData.ConsecutiveDays)

	// 如果自動發放獎勵，生成命令
	if config.AutoClaim {
		commands := s.rewardManager.BuildRewardCommand(config.Rewards, "handle_event_result")
		result.Commands = commands
	}

	return result, nil
}

// processTaskComplete 處理任務完成
func (s *UnifiedEventService) processTaskComplete(userID, eventID string, config *EventItemConfig, userData *UnifiedUserEventData, result *EventResult) (*EventResult, error) {
	// 這裡可以根據 Parameters 中的任務配置來檢查任務完成情況
	// 簡化處理，假設任務已完成

	result.Rewards = config.Rewards
	result.Message = fmt.Sprintf("任務完成：%s", config.Name)

	// 如果自動發放獎勵，生成命令
	if config.AutoClaim {
		commands := s.rewardManager.BuildRewardCommand(config.Rewards, "handle_event_result")
		result.Commands = commands
	}

	return result, nil
}

// processHoliday 處理節日活動
func (s *UnifiedEventService) processHoliday(userID, eventID string, config *EventItemConfig, userData *UnifiedUserEventData, result *EventResult) (*EventResult, error) {
	// 節日活動的特殊邏輯
	result.Rewards = config.Rewards
	result.Message = fmt.Sprintf("參與節日活動：%s", config.Name)

	// 如果自動發放獎勵，生成命令
	if config.AutoClaim {
		commands := s.rewardManager.BuildRewardCommand(config.Rewards, "handle_event_result")
		result.Commands = commands
	}

	return result, nil
}

// GetEventData 獲取用戶活動數據
func (s *UnifiedEventService) GetEventData(userID, eventID string) (*UnifiedUserEventData, error) {
	return s.storage.GetEventData(userID, eventID)
}

// GetAllEventData 獲取用戶所有活動數據
func (s *UnifiedEventService) GetAllEventData(userID string) (map[string]*UnifiedUserEventData, error) {
	return s.storage.GetAllEventData(userID)
}

// GetEventConfig 獲取活動配置
func (s *UnifiedEventService) GetEventConfig(eventID string) (*EventItemConfig, bool) {
	config, exists := s.config.Events[eventID]
	return config, exists
}

// GetAllEventConfigs 獲取所有活動配置
func (s *UnifiedEventService) GetAllEventConfigs() map[string]*EventItemConfig {
	return s.config.Events
}
