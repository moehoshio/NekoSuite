package wish

import (
	"fmt"
	"math/rand"
	"time"

	common "neko-suite/go"
)

// UnifiedService 統一的祈願服務
type UnifiedService struct {
	config        *Config
	storage       *WishStorageAdapter
	commonStorage common.CommonStorage
	rng           *rand.Rand
	ticketManager *WishTicketManager
	limitManager  *common.LimitModeManager
}

// NewUnifiedWishService 創建統一的祈願服務實例
func NewUnifiedWishService(configPath string) (*UnifiedService, error) {
	cfg, err := LoadConfig(configPath)
	if err != nil {
		return nil, fmt.Errorf("failed to load config: %w", err)
	}

	// 創建統一存儲
	commonStorage, err := common.NewStorage(cfg.Storage)
	if err != nil {
		return nil, fmt.Errorf("failed to create storage: %w", err)
	}

	// 創建 wish 存儲適配器
	wishStorage := NewWishStorageAdapter(commonStorage)

	s := &UnifiedService{
		config:        cfg,
		storage:       wishStorage,
		commonStorage: commonStorage,
		rng:           rand.New(rand.NewSource(time.Now().UnixNano())),
		ticketManager: NewWishTicketManager(),
		limitManager:  common.NewLimitModeManager(commonStorage),
	}

	// 初始化祈願券類型
	s.initTicketTypes()

	return s, nil
}

// UnifiedWishResult 統一的祈願結果
type UnifiedWishResult struct {
	Items       []string       `json:"items"`
	Pool        string         `json:"pool"`
	Count       int            `json:"count"`
	Cost        map[string]int `json:"cost"`
	TicketsUsed map[string]int `json:"tickets_used"`
	Timestamp   time.Time      `json:"timestamp"`
}

// convertLimitModes 轉換限制模式配置
func convertLimitModes(oldLimit *LimitModes) *common.LimitModeConfig {
	if oldLimit == nil {
		return nil
	}

	return &common.LimitModeConfig{
		Count:          oldLimit.Count,
		Time:           oldLimit.Time,
		RefreshAtTime:  oldLimit.RefreshAtTime,
		RefreshAtWeek:  oldLimit.RefreshAtWeek,
		RefreshAtMonth: oldLimit.RefreshAtMonth,
	}
}

// ProcessWish 處理祈願請求（統一版本）
func (s *UnifiedService) ProcessWish(userID string, poolName string, count int) (*UnifiedWishResult, error) {
	// 檢查池配置
	poolConfig, exists := s.config.Pools[poolName]
	if !exists {
		return nil, fmt.Errorf("未知的祈願池: %s", poolName)
	}

	// 轉換限制模式配置
	var limitConfig *common.LimitModeConfig
	if poolConfig.LimitModes != nil {
		limitConfig = convertLimitModes(poolConfig.LimitModes)
	}

	// 檢查限制模式
	if limitConfig != nil {
		canWish, err := s.limitManager.CanPerform(userID, "wish", poolName, count, limitConfig)
		if err != nil {
			return nil, fmt.Errorf("檢查限制模式失敗: %w", err)
		}
		if !canWish {
			return nil, fmt.Errorf("已達到祈願次數限制")
		}
	}

	// 執行祈願
	result, err := s.executeWish(userID, poolName, poolConfig, count)
	if err != nil {
		return nil, err
	}

	// 更新限制模式記錄
	if limitConfig != nil {
		if err := s.limitManager.RecordUsage(userID, "wish", poolName, count, limitConfig); err != nil {
			return nil, fmt.Errorf("更新限制模式記錄失敗: %w", err)
		}
	}

	// 轉換結果為統一格式
	unifiedResult := &UnifiedWishResult{
		Items:       result.Items,
		Pool:        poolName,
		Count:       count,
		Cost:        map[string]int{"default": result.Cost},
		TicketsUsed: result.TicketsUsed,
		Timestamp:   time.Now(),
	}

	// 保存祈願歷史記錄
	if s.config.History != nil {
		historyRecord := UnifiedWishRecord{
			Pool:        poolName,
			Count:       count,
			Items:       result.Items,
			Cost:        result.Cost,
			TicketsUsed: result.TicketsUsed,
			Timestamp:   time.Now(),
		}

		if err := s.storage.AddWishHistory(userID, historyRecord); err != nil {
			return nil, fmt.Errorf("保存祈願歷史記錄失敗: %w", err)
		}
	}

	return unifiedResult, nil
}

// executeWish 執行祈願邏輯
func (s *UnifiedService) executeWish(userID, poolName string, poolConfig WishPoolConfig, count int) (*WishResult, error) {
	// 驗證持續時間
	if poolConfig.Duration != nil {
		if err := s.validateDuration(poolConfig.Duration); err != nil {
			return nil, err
		}
	}

	// 初始化結果
	result := &WishResult{
		Items:       make([]string, 0, count),
		Cost:        0,
		TicketsUsed: make(map[string]int),
	}

	// 處理每次祈願
	for i := 0; i < count; i++ {
		// 檢查是否觸發保底
		_, guaranteeCount, err := s.storage.GetWishStats(userID, poolName)
		if err != nil {
			return nil, fmt.Errorf("獲取祈願統計失敗: %w", err)
		}

		var selectedItem string
		useGuarantee := false

		// 檢查保底條件（例如：90 次必出 5 星）
		if poolConfig.MaxCount > 0 && guaranteeCount >= poolConfig.MaxCount-1 {
			// 從保底物品中選擇
			if len(poolConfig.GuaranteeItems) > 0 {
				selectedItem, err = s.selectItemFromPool(poolConfig.GuaranteeItems)
				useGuarantee = true
			} else {
				selectedItem, err = s.selectItemFromPool(poolConfig.Items)
			}
		} else {
			// 正常祈願
			selectedItem, err = s.selectItemFromPool(poolConfig.Items)
		}

		if err != nil {
			return nil, fmt.Errorf("選擇物品失敗: %w", err)
		}

		result.Items = append(result.Items, selectedItem)

		// 更新統計
		if err := s.storage.IncrementTotalWishes(userID, poolName, 1); err != nil {
			return nil, fmt.Errorf("更新總祈願次數失敗: %w", err)
		}

		if useGuarantee {
			// 重置保底計數 - 手動設置為 0
			wishData, err := s.storage.GetWishData(userID)
			if err != nil {
				return nil, fmt.Errorf("獲取祈願數據失敗: %w", err)
			}
			wishData.Stats.GuaranteeCount[poolName] = 0
			if err := s.storage.SaveWishData(userID, wishData); err != nil {
				return nil, fmt.Errorf("重置保底計數失敗: %w", err)
			}
		} else {
			// 增加保底計數
			if err := s.storage.IncrementGuaranteeCount(userID, poolName); err != nil {
				return nil, fmt.Errorf("增加保底計數失敗: %w", err)
			}
		}
	}

	// 處理消費
	if err := s.processWishCost(userID, poolConfig, count, result); err != nil {
		return nil, err
	}

	return result, nil
}

// processWishCost 處理祈願消費
func (s *UnifiedService) processWishCost(userID string, poolConfig WishPoolConfig, count int, result *WishResult) error {
	// 自動消費模式
	if poolConfig.AutoCost {
		// 優先使用適用的祈願券
		for _, ticketConfig := range s.config.Tickets {
			// 檢查是否適用於當前池
			applicable := false
			for _, pool := range ticketConfig.ApplicablePools {
				if pool == poolConfig.Dir || pool == "*" {
					applicable = true
					break
				}
			}

			if !applicable {
				continue
			}

			// 檢查用戶是否有足夠的祈願券
			userTickets, err := s.storage.GetWishTickets(userID, ticketConfig.ID)
			if err != nil {
				continue
			}

			// 計算需要消耗的券數
			requiredTickets := 0
			remainingCount := count

			for remainingCount > 0 {
				deductCount := ticketConfig.DeductCount
				if deductCount > remainingCount {
					deductCount = remainingCount
				}

				if ticketConfig.DeductMode == "batch" {
					// 批次模式：一次性扣除多次祈願
					requiredTickets++
					remainingCount -= deductCount
				} else {
					// 單次模式：每次祈願扣除一張券
					requiredTickets += deductCount
					remainingCount -= deductCount
				}
			}

			if userTickets >= requiredTickets {
				// 扣除祈願券
				if err := s.storage.UpdateWishTickets(userID, ticketConfig.ID, -requiredTickets); err != nil {
					return fmt.Errorf("扣除祈願券失敗: %w", err)
				}
				result.TicketsUsed[ticketConfig.ID] = requiredTickets
				return nil
			}
		}

		// 如果沒有可用的祈願券，使用普通消費
		if len(poolConfig.Cost) > 0 {
			// 取第一個消費項目作為默認消費（簡化處理）
			for _, costPerWish := range poolConfig.Cost {
				result.Cost = costPerWish * count
				break // 只處理第一個消費項目
			}
		}
	}

	return nil
}

// selectItemFromPool 從物品池中選擇物品
func (s *UnifiedService) selectItemFromPool(items map[string]WishItemConfig) (string, error) {
	if len(items) == 0 {
		return "", fmt.Errorf("物品池為空")
	}

	// 構建概率表
	type itemProb struct {
		name string
		prob float64
	}

	var probTable []itemProb
	totalProb := 0.0

	for name, config := range items {
		// 如果有子列表，從子列表中選擇
		if len(config.SubList) > 0 {
			subItem, err := s.selectItemFromSubList(config.SubList)
			if err != nil {
				return "", err
			}
			probTable = append(probTable, itemProb{name: subItem, prob: config.Probability})
		} else {
			probTable = append(probTable, itemProb{name: name, prob: config.Probability})
		}
		totalProb += config.Probability
	}

	// 歸一化概率
	if totalProb <= 0 {
		return "", fmt.Errorf("總概率必須大於 0")
	}

	for i := range probTable {
		probTable[i].prob /= totalProb
	}

	// 隨機選擇
	roll := s.rng.Float64()
	cumProb := 0.0

	for _, item := range probTable {
		cumProb += item.prob
		if roll <= cumProb {
			return item.name, nil
		}
	}

	// 兜底返回最後一個物品
	if len(probTable) > 0 {
		return probTable[len(probTable)-1].name, nil
	}

	return "", fmt.Errorf("無法選擇物品")
}

// selectItemFromSubList 從子列表中選擇物品
func (s *UnifiedService) selectItemFromSubList(subList map[string]float64) (string, error) {
	if len(subList) == 0 {
		return "", fmt.Errorf("子列表為空")
	}

	type itemProb struct {
		name string
		prob float64
	}

	var probTable []itemProb
	totalProb := 0.0

	for name, prob := range subList {
		probTable = append(probTable, itemProb{name: name, prob: prob})
		totalProb += prob
	}

	// 歸一化概率
	if totalProb <= 0 {
		return "", fmt.Errorf("總概率必須大於 0")
	}

	for i := range probTable {
		probTable[i].prob /= totalProb
	}

	// 隨機選擇
	roll := s.rng.Float64()
	cumProb := 0.0

	for _, item := range probTable {
		cumProb += item.prob
		if roll <= cumProb {
			return item.name, nil
		}
	}

	// 兜底返回最後一個物品
	if len(probTable) > 0 {
		return probTable[len(probTable)-1].name, nil
	}

	return "", fmt.Errorf("無法選擇物品")
}

// validateDuration 驗證持續時間
func (s *UnifiedService) validateDuration(duration *Duration) error {
	now := time.Now()

	if duration.StartDate != "" {
		startTime, err := time.Parse(time.RFC3339, duration.StartDate)
		if err != nil {
			return fmt.Errorf("無效的開始時間格式: %w", err)
		}
		if now.Before(startTime) {
			return fmt.Errorf("活動尚未開始")
		}
	}

	if duration.EndDate != "" {
		endTime, err := time.Parse(time.RFC3339, duration.EndDate)
		if err != nil {
			return fmt.Errorf("無效的結束時間格式: %w", err)
		}
		if now.After(endTime) {
			return fmt.Errorf("活動已結束")
		}
	}

	return nil
}

// initTicketTypes 初始化祈願券類型
func (s *UnifiedService) initTicketTypes() {
	if s.ticketManager != nil && len(s.config.Tickets) > 0 {
		// 這裡可以執行一些初始化邏輯
		// 例如驗證配置、設置默認值等
	}
}

// GetWishStats 獲取祈願統計
func (s *UnifiedService) GetWishStats(userID, poolName string) (totalWishes int, guaranteeCount int, currentCount int, err error) {
	totalWishes, guaranteeCount, err = s.storage.GetWishStats(userID, poolName)
	if err != nil {
		return 0, 0, 0, err
	}
	currentCount, err = s.storage.GetWishCount(userID, poolName)
	return totalWishes, guaranteeCount, currentCount, err
}

// GetWishTickets 獲取祈願券數量
func (s *UnifiedService) GetWishTickets(userID, ticketType string) (int, error) {
	return s.storage.GetWishTickets(userID, ticketType)
}

// AddWishTickets 添加祈願券
func (s *UnifiedService) AddWishTickets(userID, ticketType string, amount int) error {
	return s.storage.AddWishTickets(userID, ticketType, amount)
}
