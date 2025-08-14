package wish

import (
	"fmt"
	"math"
	"math/rand"
	"sort"
	"strconv"
	"time"
)

// Service 祈愿服务
type Service struct {
	config           *Config
	storage          Storage
	rng              *rand.Rand
	ticketManager    *WishTicketManager
	limitModeManager *LimitModeManager
}

// NewWishService 创建祈愿服务实例
func NewWishService(configPath string) (*Service, error) {
	cfg, err := LoadConfig(configPath)
	if err != nil {
		return nil, fmt.Errorf("failed to load config: %w", err)
	}

	// 根据配置创建存储实例
	store, err := CreateStorage(cfg)
	if err != nil {
		return nil, fmt.Errorf("failed to create storage: %w", err)
	}

	ticketManager := NewWishTicketManager()

	// 創建共用的限制模式管理器（先暫時使用舊的管理器）
	limitModeManager := NewLimitModeManager(store)

	// 创建服务实例
	s := &Service{
		config:           cfg,
		storage:          store,
		rng:              rand.New(rand.NewSource(time.Now().UnixNano())),
		ticketManager:    ticketManager,
		limitModeManager: limitModeManager,
	}

	// 初始化祈愿券类型
	s.initTicketTypes()

	return s, nil
}

// initTicketTypes 从配置初始化祈愿券类型
func (s *Service) initTicketTypes() {
	// 先注册配置文件中的祈愿券类型
	if s.config.Tickets != nil {
		for _, ticketConfig := range s.config.Tickets {
			deductMode := "flexible" // 默认为灵活模式
			if ticketConfig.DeductMode != "" {
				deductMode = ticketConfig.DeductMode
			}

			s.ticketManager.RegisterTicketType(WishTicketType{
				ID:              ticketConfig.ID,
				ApplicablePools: ticketConfig.ApplicablePools,
				Value:           80, // 默认抵扣值，这里可以根据需要调整
				DeductCount:     ticketConfig.DeductCount,
				DeductMode:      deductMode,
			})
		}
	}

	// 为了兼容性，如果配置中没有祈愿券类型，则注册默认类型
	if len(s.config.Tickets) == 0 {
		s.initDefaultTicketTypes()
	}
}

// initDefaultTicketTypes 初始化默认祈愿券类型（兼容旧版本）
func (s *Service) initDefaultTicketTypes() {
	// 通用祈愿券 - 适用于所有池
	s.ticketManager.RegisterTicketType(WishTicketType{
		ID:              "universal",
		ApplicablePools: []string{}, // 空表示适用于所有池
		Value:           80,         // 抵扣80金币
		DeductCount:     1,
		DeductMode:      "flexible",
	})

	// 星途祈愿券 - 仅适用于星途池
	s.ticketManager.RegisterTicketType(WishTicketType{
		ID:              "starpath",
		ApplicablePools: []string{"Starpath"},
		Value:           80,
		DeductCount:     1,
		DeductMode:      "flexible",
	})

	// 星光如梦祈愿券 - 仅适用于星光如梦池
	s.ticketManager.RegisterTicketType(WishTicketType{
		ID:              "starrymirage",
		ApplicablePools: []string{"StarryMirage"},
		Value:           80,
		DeductCount:     1,
		DeductMode:      "flexible",
	})

	// 红玫的誓约祈愿券 - 仅适用于红玫的誓约池
	s.ticketManager.RegisterTicketType(WishTicketType{
		ID:              "oathoftheredrose",
		ApplicablePools: []string{"OathOfTheRedRose"},
		Value:           80,
		DeductCount:     1,
		DeductMode:      "flexible",
	})

	// 微光之愿祈愿券 - 仅适用于每日祈愿（通常免费，这里作为特殊奖励）
	s.ticketManager.RegisterTicketType(WishTicketType{
		ID:              "glimmeringprayer",
		ApplicablePools: []string{"GlimmeringPrayer"},
		Value:           0, // 微光之愿通常免费
		DeductCount:     1,
		DeductMode:      "flexible",
	})
}

// WishResult 祈愿结果
type WishResult struct {
	Items       []string       `json:"items"`
	Cost        int            `json:"cost"`
	TicketsUsed map[string]int `json:"tickets_used,omitempty"`
}

// PerformWish 执行祈愿
func (s *Service) PerformWish(user string, poolType string, count int) (*WishResult, error) {
	// 验证参数
	if user == "" {
		return nil, fmt.Errorf("invalid_user_name")
	}

	poolConfig, exists := s.config.GetPoolConfig(poolType)
	if !exists {
		return nil, fmt.Errorf("pool_not_found")
	}

	// 检查祈愿池的持续时间
	if err := s.limitModeManager.CheckPoolDuration(poolConfig); err != nil {
		return nil, err
	}

	// 检查限制模式
	if err := s.limitModeManager.CheckLimitMode(user, poolType, poolConfig, count); err != nil {
		return nil, err
	}
	if !exists {
		return nil, fmt.Errorf("invalid_wish_type")
	}

	// 基本参数验证
	if count <= 0 {
		return nil, fmt.Errorf("invalid_wish_value")
	}

	// 检查祈愿池的持续时间
	if err := s.limitModeManager.CheckPoolDuration(poolConfig); err != nil {
		return nil, err
	}

	// 检查限制模式
	if err := s.limitModeManager.CheckLimitMode(user, poolType, poolConfig, count); err != nil {
		return nil, err
	}

	// 计算费用
	originalCost := s.calculateCost(poolConfig, count)

	// 检查是否为无效的祈愿次数（当 auto_cost 为 false 且次数不在 cost 列表中时）
	if originalCost == -1 {
		return nil, fmt.Errorf("invalid_wish_count")
	}

	// 计算使用祈愿券后的费用和券使用情况
	var err error
	finalCost, ticketsUsed, err := s.ticketManager.CalculateCostWithTickets(s.storage, user, poolType, originalCost, count)
	if err != nil {
		return nil, fmt.Errorf("failed to calculate cost with tickets: %w", err)
	}

	// 使用祈愿券
	if len(ticketsUsed) > 0 {
		if err := s.ticketManager.UseTickets(s.storage, user, ticketsUsed); err != nil {
			return nil, fmt.Errorf("failed to use tickets: %w", err)
		}
	}

	// 生成祈愿物品
	items := make([]string, 0, count)

	for i := 0; i < count; i++ {
		// 检查保底
		guaranteeItem, err := s.checkGuarantee(user, poolType, poolConfig)
		if err != nil {
			return nil, fmt.Errorf("failed to check guarantee: %w", err)
		}

		var item string
		var isGuarantee bool
		if guaranteeItem != "" {
			item = guaranteeItem
			isGuarantee = true
			// 保底获得时增加保底计数
			if err := s.storage.IncrementGuaranteeCount(user, poolType); err != nil {
				return nil, fmt.Errorf("failed to increment guarantee count: %w", err)
			}
		} else {
			// 普通抽取
			item = s.getRandomItem(poolConfig.Items)
			isGuarantee = false
		}

		// 只有非保底情况下才更新祈愿计数（用于保底系统）
		// 如果池没有保底机制（没有max_count或guarantee_items），则不进行计数
		if !isGuarantee && poolConfig.MaxCount > 0 && len(poolConfig.GuaranteeItems) > 0 {
			if err := s.incrementWishCount(user, poolType); err != nil {
				return nil, fmt.Errorf("failed to update wish count: %w", err)
			}
		}

		// 更新累计祈愿统计（包括每日祈愿和保底）
		if err := s.storage.IncrementTotalWishes(user, poolType, 1); err != nil {
			return nil, fmt.Errorf("failed to update total wishes: %w", err)
		}

		items = append(items, item)
	}

	// 更新限制模式使用次数
	if err := s.limitModeManager.IncrementLimitModeUsage(user, poolType, poolConfig, count); err != nil {
		return nil, fmt.Errorf("failed to update limit mode usage: %w", err)
	}

	// 记录祈愿历史
	wishRecord := WishRecord{
		Pool:        poolType,
		Count:       count,
		Items:       items,
		Cost:        finalCost,
		TicketsUsed: ticketsUsed,
		Timestamp:   time.Now(),
	}

	if err := s.storage.AddWishHistoryWithConfig(user, wishRecord, s.config.History); err != nil {
		// 历史记录失败不应该影响祈愿结果，只记录错误
		fmt.Printf("Warning: failed to add wish history for user %s: %v\n", user, err)
	}

	result := &WishResult{
		Items: items,
		Cost:  finalCost,
	}

	// 只有在使用了祈愿券时才添加到结果中
	if len(ticketsUsed) > 0 {
		result.TicketsUsed = ticketsUsed
	}

	return result, nil
}

// PerformWishWithBalance 执行祈愿（包含余额检查）
func (s *Service) PerformWishWithBalance(user string, poolType string, count int, userBalance float64) (*WishResult, error) {
	// 验证参数
	if user == "" {
		return nil, fmt.Errorf("invalid_user_name")
	}

	poolConfig, exists := s.config.GetPoolConfig(poolType)
	if !exists {
		return nil, fmt.Errorf("invalid_wish_type")
	}

	// 基本参数验证
	if count <= 0 {
		return nil, fmt.Errorf("invalid_wish_value")
	}

	// 检查祈愿池的持续时间
	if err := s.limitModeManager.CheckPoolDuration(poolConfig); err != nil {
		return nil, err
	}

	// 检查限制模式
	if err := s.limitModeManager.CheckLimitMode(user, poolType, poolConfig, count); err != nil {
		return nil, err
	}

	// 计算费用
	originalCost := s.calculateCost(poolConfig, count)

	// 检查是否为无效的祈愿次数（当 auto_cost 为 false 且次数不在 cost 列表中时）
	if originalCost == -1 {
		return nil, fmt.Errorf("invalid_wish_count")
	}

	// 计算使用祈愿券后的费用和券使用情况
	var err error
	finalCost, ticketsUsed, err := s.ticketManager.CalculateCostWithTickets(s.storage, user, poolType, originalCost, count)
	if err != nil {
		return nil, fmt.Errorf("failed to calculate cost with tickets: %w", err)
	}

	// 余额检查：如果最终费用 > 0 且余额不足（包括负数或小数），则返回错误
	if finalCost > 0 && userBalance < float64(finalCost) {
		return nil, fmt.Errorf("insufficient_balance")
	}

	// 使用祈愿券
	if len(ticketsUsed) > 0 {
		if err := s.ticketManager.UseTickets(s.storage, user, ticketsUsed); err != nil {
			return nil, fmt.Errorf("failed to use tickets: %w", err)
		}
	}

	// 生成祈愿物品
	items := make([]string, 0, count)

	for i := 0; i < count; i++ {
		// 检查保底
		guaranteeItem, err := s.checkGuarantee(user, poolType, poolConfig)
		if err != nil {
			return nil, fmt.Errorf("failed to check guarantee: %w", err)
		}

		var item string
		var isGuarantee bool
		if guaranteeItem != "" {
			item = guaranteeItem
			isGuarantee = true
			// 保底获得时增加保底计数
			if err := s.storage.IncrementGuaranteeCount(user, poolType); err != nil {
				return nil, fmt.Errorf("failed to increment guarantee count: %w", err)
			}
		} else {
			// 普通抽取
			item = s.getRandomItem(poolConfig.Items)
			isGuarantee = false
		}

		// 只有非保底情况下才更新祈愿计数（用于保底系统）
		// 如果池没有保底机制（没有max_count或guarantee_items），则不进行计数
		if !isGuarantee && poolConfig.MaxCount > 0 && len(poolConfig.GuaranteeItems) > 0 {
			if err := s.incrementWishCount(user, poolType); err != nil {
				return nil, fmt.Errorf("failed to update wish count: %w", err)
			}
		}

		// 更新累计祈愿统计（包括每日祈愿和保底）
		if err := s.storage.IncrementTotalWishes(user, poolType, 1); err != nil {
			return nil, fmt.Errorf("failed to update total wishes: %w", err)
		}

		items = append(items, item)
	}

	// 更新限制模式使用次数
	if err := s.limitModeManager.IncrementLimitModeUsage(user, poolType, poolConfig, count); err != nil {
		return nil, fmt.Errorf("failed to update limit mode usage: %w", err)
	}

	// 记录祈愿历史
	wishRecord := WishRecord{
		Pool:        poolType,
		Count:       count,
		Items:       items,
		Cost:        finalCost,
		TicketsUsed: ticketsUsed,
		Timestamp:   time.Now(),
	}

	if err := s.storage.AddWishHistoryWithConfig(user, wishRecord, s.config.History); err != nil {
		// 历史记录失败不应该影响祈愿结果，只记录错误
		fmt.Printf("Warning: failed to add wish history for user %s: %v\n", user, err)
	}

	result := &WishResult{
		Items: items,
		Cost:  finalCost,
	}

	// 只有在使用了祈愿券时才添加到结果中
	if len(ticketsUsed) > 0 {
		result.TicketsUsed = ticketsUsed
	}

	return result, nil
}

// PerformDailyWish 执行每日祈愿 (已废弃，建议使用 PerformWish)
func (s *Service) PerformDailyWish(user string) (*WishResult, error) {
	// 直接使用通用的祈愿方法
	return s.PerformWish(user, "GlimmeringPrayer", 1)
}

// QueryWishCount 查询祈愿次数
func (s *Service) QueryWishCount(user string, poolType string) (int, error) {
	if user == "" {
		return 0, fmt.Errorf("invalid_user_name")
	}

	_, exists := s.config.GetPoolConfig(poolType)
	if !exists {
		return 0, fmt.Errorf("invalid_wish_type")
	}

	count, err := s.storage.GetWishCount(user, poolType)
	if err != nil {
		return 0, fmt.Errorf("failed to get wish count: %w", err)
	}

	return count, nil
}

// checkGuarantee 检查保底
func (s *Service) checkGuarantee(user string, poolType string, config WishPoolConfig) (string, error) {
	// 如果池配置中没有 max_count 或 guarantee_items，则视为没有保底
	if config.MaxCount <= 0 || len(config.GuaranteeItems) == 0 {
		return "", nil
	}

	// 使用共享计数或者池特定计数
	countType := poolType
	if config.CountsName != "" {
		countType = config.CountsName
	}

	currentCount, err := s.storage.GetWishCount(user, countType)
	if err != nil {
		return "", err
	}

	// 当计数大于或等于 max_count 时触发保底
	if currentCount >= config.MaxCount {
		// 触发保底，扣除一次 max_count
		newCount := currentCount - config.MaxCount
		if err := s.storage.UpdateWishCount(user, countType, newCount); err != nil {
			return "", err
		}

		// 从保底物品中随机选择
		return s.getRandomItem(config.GuaranteeItems), nil
	}

	return "", nil
}

// incrementWishCount 增加祈愿次数
func (s *Service) incrementWishCount(user string, poolType string) error {
	// 获取池配置以确定使用的计数名称
	poolConfig, exists := s.config.GetPoolConfig(poolType)
	if !exists {
		return fmt.Errorf("pool config not found for %s", poolType)
	}

	// 使用共享计数或者池特定计数
	countType := poolType
	if poolConfig.CountsName != "" {
		countType = poolConfig.CountsName
	}

	currentCount, err := s.storage.GetWishCount(user, countType)
	if err != nil {
		return err
	}

	return s.storage.UpdateWishCount(user, countType, currentCount+1)
}

// getRandomItem 根据权重随机选择物品，支持复合类型
func (s *Service) getRandomItem(items map[string]WishItemConfig) string {
	// 计算总权重
	totalWeight := 0.0
	for _, itemConfig := range items {
		totalWeight += itemConfig.Probability
	}

	// 生成随机数
	randValue := s.rng.Float64() * totalWeight

	// 选择物品
	currentWeight := 0.0
	for itemName, itemConfig := range items {
		currentWeight += itemConfig.Probability
		if randValue <= currentWeight {
			// 如果是复合类型，从子列表中再次随机选择
			if itemConfig.IsCompound() {
				return s.getRandomSubItem(itemConfig.SubList)
			}
			// 简单类型，直接返回物品名称
			return itemName
		}
	}

	// 备用：如果没有选中任何物品，返回第一个
	for itemName, itemConfig := range items {
		if itemConfig.IsCompound() {
			return s.getRandomSubItem(itemConfig.SubList)
		}
		return itemName
	}

	return ""
}

// getRandomSubItem 从子列表中随机选择物品
func (s *Service) getRandomSubItem(subItems map[string]float64) string {
	// 计算总权重
	totalWeight := 0.0
	for _, weight := range subItems {
		totalWeight += weight
	}

	// 生成随机数
	randValue := s.rng.Float64() * totalWeight

	// 选择物品
	currentWeight := 0.0
	for item, weight := range subItems {
		currentWeight += weight
		if randValue <= currentWeight {
			return item
		}
	}

	// 备用：如果没有选中任何物品，返回第一个
	for item := range subItems {
		return item
	}

	return ""
}

// calculateCost 计算祈愿费用
func (s *Service) calculateCost(config WishPoolConfig, count int) int {
	if config.Cost == nil {
		return 0
	}

	countStr := fmt.Sprintf("%d", count)

	// 首先检查是否有精确匹配的费用配置
	if cost, exists := config.Cost[countStr]; exists {
		return cost
	}

	// 如果启用了 auto_cost，计算最优费用
	if config.AutoCost {
		return s.calculateOptimalCost(config.Cost, count)
	}

	// auto_cost 为 false 时，只接受 cost 列表中的次数，其他次数视为无效
	// 返回 -1 表示无效的祈愿次数
	return -1
}

// calculateOptimalCost 计算最优费用（当 auto_cost 为 true 时使用）
func (s *Service) calculateOptimalCost(costMap map[string]int, targetCount int) int {
	if targetCount <= 0 {
		return 0
	}

	// 获取所有可用的次数和费用配置
	type CostOption struct {
		Count int
		Cost  int
		Rate  float64 // 单次费用率
	}

	var options []CostOption
	for countStr, cost := range costMap {
		if count, err := strconv.Atoi(countStr); err == nil && count > 0 {
			rate := float64(cost) / float64(count)
			options = append(options, CostOption{
				Count: count,
				Cost:  cost,
				Rate:  rate,
			})
		}
	}

	if len(options) == 0 {
		return 0
	}

	// 按单次费用率排序，找到最优组合
	sort.Slice(options, func(i, j int) bool {
		return options[i].Rate < options[j].Rate
	})

	// 动态规划求解最优费用
	// dp[i] 表示达到 i 次祈愿的最小费用
	dp := make([]int, targetCount+1)
	for i := 1; i <= targetCount; i++ {
		dp[i] = math.MaxInt32
	}

	for i := 1; i <= targetCount; i++ {
		for _, option := range options {
			if option.Count <= i {
				if dp[i-option.Count] != math.MaxInt32 {
					dp[i] = min(dp[i], dp[i-option.Count]+option.Cost)
				}
			}
		}
	}

	if dp[targetCount] == math.MaxInt32 {
		// 如果无法组合到目标次数，使用最便宜的单次费用
		cheapestRate := options[0].Rate
		return int(cheapestRate * float64(targetCount))
	}

	return dp[targetCount]
}

// min 辅助函数
func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// MakeWish 执行祈愿的便利方法 (PerformWish 的别名)
func (s *Service) MakeWish(user string, poolType string, count int) (*WishResult, error) {
	return s.PerformWish(user, poolType, count)
}

// GetUserData 获取用户数据的便利方法
func (s *Service) GetUserData(user string) (*UserData, error) {
	return s.storage.GetUserData(user)
}

// AddWishTickets 添加祈愿券的便利方法
func (s *Service) AddWishTickets(user string, ticketType string, amount int) error {
	return s.storage.AddWishTickets(user, ticketType, amount)
}

// GetWishTickets 获取祈愿券数量的便利方法
func (s *Service) GetWishTickets(user string, ticketType string) (int, error) {
	return s.storage.GetWishTickets(user, ticketType)
}
