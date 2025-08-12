package wish

// WishTicketType 祈愿券类型定义
type WishTicketType struct {
	ID              string   `yaml:"id"`               // 券类型ID
	ApplicablePools []string `yaml:"applicable_pools"` // 适用的祈愿池，空表示全部池
	Value           int      `yaml:"value"`            // 券价值（可抵扣的金币数）
	DeductCount     int      `yaml:"deduct_count"`     // 可抵扣的祈愿次数
	DeductMode      string   `yaml:"deduct_mode"`      // 抵扣模式：flexible(灵活), fixed(固定)
}

// WishTicketManager 祈愿券管理器
type WishTicketManager struct {
	TicketTypes map[string]WishTicketType
}

// NewWishTicketManager 创建祈愿券管理器
func NewWishTicketManager() *WishTicketManager {
	return &WishTicketManager{
		TicketTypes: make(map[string]WishTicketType),
	}
}

// RegisterTicketType 注册祈愿券类型
func (wtm *WishTicketManager) RegisterTicketType(ticketType WishTicketType) {
	wtm.TicketTypes[ticketType.ID] = ticketType
}

// GetTicketType 获取祈愿券类型信息
func (wtm *WishTicketManager) GetTicketType(ticketID string) (WishTicketType, bool) {
	ticketType, exists := wtm.TicketTypes[ticketID]
	return ticketType, exists
}

// CanUseForPool 检查祈愿券是否可用于指定祈愿池
func (wtm *WishTicketManager) CanUseForPool(ticketID string, poolName string) bool {
	ticketType, exists := wtm.TicketTypes[ticketID]
	if !exists {
		return false
	}

	// 如果适用池列表为空，表示适用于所有池
	if len(ticketType.ApplicablePools) == 0 {
		return true
	}

	// 检查指定池是否在适用列表中
	for _, pool := range ticketType.ApplicablePools {
		if pool == poolName {
			return true
		}
	}

	return false
}

// GetUsableTickets 获取用户在指定祈愿池可用的祈愿券
func (wtm *WishTicketManager) GetUsableTickets(storage Storage, user string, poolName string) (map[string]int, error) {
	usableTickets := make(map[string]int)

	for ticketID := range wtm.TicketTypes {
		if wtm.CanUseForPool(ticketID, poolName) {
			count, err := storage.GetWishTickets(user, ticketID)
			if err != nil {
				return nil, err
			}
			if count > 0 {
				usableTickets[ticketID] = count
			}
		}
	}

	return usableTickets, nil
}

// CalculateCostWithTickets 计算使用祈愿券后的费用
func (wtm *WishTicketManager) CalculateCostWithTickets(storage Storage, user string, poolName string, originalCost int, wishCount int) (finalCost int, ticketsUsed map[string]int, err error) {
	ticketsUsed = make(map[string]int)
	remainingCost := originalCost

	// 获取可用的祈愿券
	usableTickets, err := wtm.GetUsableTickets(storage, user, poolName)
	if err != nil {
		return originalCost, ticketsUsed, err
	}

	// 按券的价值排序，优先使用价值高的券
	// 这里简化实现，可以根据需要优化排序逻辑
	for ticketID, available := range usableTickets {
		if remainingCost <= 0 {
			break
		}

		ticketType, exists := wtm.TicketTypes[ticketID]
		if !exists {
			continue
		}

		// 根据抵扣模式处理
		if ticketType.DeductMode == "fixed" {
			// 固定模式：只能用于特定次数的祈愿
			if wishCount == ticketType.DeductCount && available > 0 {
				// 可以使用这张券完全抵扣
				ticketsUsed[ticketID] = 1
				remainingCost = 0 // 固定模式通常完全抵扣
				break
			}
		} else {
			// 灵活模式（默认）：可以按需抵扣
			deductCount := ticketType.DeductCount
			if deductCount <= 0 {
				deductCount = 1 // 默认抵扣1次
			}

			// 计算能抵扣的祈愿次数
			maxDeductWishes := available * deductCount
			if maxDeductWishes >= wishCount {
				// 有足够的券抵扣全部祈愿
				neededTickets := (wishCount + deductCount - 1) / deductCount // 向上取整
				if neededTickets > available {
					neededTickets = available
				}
				ticketsUsed[ticketID] = neededTickets
				remainingCost = 0
				break
			} else {
				// 部分抵扣
				if available > 0 {
					ticketsUsed[ticketID] = available
					deductedWishes := available * deductCount
					// 按比例计算抵扣的费用
					deductedCost := (originalCost * deductedWishes) / wishCount
					remainingCost -= deductedCost
				}
			}
		}
	}

	// 确保最终费用不会为负数
	if remainingCost < 0 {
		remainingCost = 0
	}

	return remainingCost, ticketsUsed, nil
}

// UseTickets 使用祈愿券（从用户账户中扣除）
func (wtm *WishTicketManager) UseTickets(storage Storage, user string, ticketsUsed map[string]int) error {
	for ticketID, count := range ticketsUsed {
		if count > 0 {
			// 减少用户的祈愿券数量
			err := storage.AddWishTickets(user, ticketID, -count)
			if err != nil {
				return err
			}
		}
	}
	return nil
}
