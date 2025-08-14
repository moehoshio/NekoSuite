package wish

import (
	"fmt"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
)

// UnifiedHandler 統一的祈願處理器
type UnifiedHandler struct {
	service *UnifiedService
}

// NewUnifiedHandler 創建統一的祈願處理器
func NewUnifiedHandler(service *UnifiedService) *UnifiedHandler {
	return &UnifiedHandler{
		service: service,
	}
}

// HandleWish 處理祈願請求
func (h *UnifiedHandler) HandleWish(c *gin.Context) {
	// 獲取操作類型
	action := c.Query("action")

	// 解析參數
	userID := c.Query("user")
	if userID == "" {
		c.String(http.StatusOK, "/wish_error_text invalid_user_name")
		return
	}

	switch action {
	case "wish":
		h.handleWishAction(c, userID)
	case "query":
		h.handleQueryAction(c, userID)
	default:
		c.String(http.StatusOK, "/wish_error_text invalid_action")
	}
}

// handleWishAction 處理祈願操作
func (h *UnifiedHandler) handleWishAction(c *gin.Context, userID string) {
	poolName := c.Query("pool")
	if poolName == "" {
		c.String(http.StatusOK, "/wish_error_text invalid_wish_type")
		return
	}

	countStr := c.Query("value")
	count := 1 // 默認1次
	if countStr != "" {
		var err error
		count, err = strconv.Atoi(countStr)
		if err != nil || count <= 0 {
			c.String(http.StatusOK, "/wish_error_text invalid_wish_value")
			return
		}
	}

	// 驗證祈願次數限制
	poolConfig, exists := h.service.config.Pools[poolName]
	if !exists {
		c.String(http.StatusOK, "/wish_error_text invalid_wish_type")
		return
	}

	if count > poolConfig.MaxCount {
		c.String(http.StatusOK, "/wish_error_text invalid_wish_count")
		return
	}

	// 處理祈願
	result, err := h.service.ProcessWish(userID, poolName, count)
	if err != nil {
		// 根據錯誤類型返回相應的錯誤命令
		switch err.Error() {
		case "insufficient balance":
			c.String(http.StatusOK, "/wish_error_text insufficient_balance")
		case "insufficient tickets":
			c.String(http.StatusOK, "/wish_error_text insufficient_tickets")
		default:
			c.String(http.StatusOK, "/wish_error_text wish_failed")
		}
		return
	}

	// 構建祈願結果命令
	// 格式: /handle_wish_<池名> <費用> <祈願次数> <物品1> <物品2> <物品3> <物品4> <物品5>

	// 計算總費用（如果有多種貨幣）
	totalCost := 0
	for _, cost := range result.Cost {
		totalCost += cost
	}

	cmd := fmt.Sprintf("/handle_wish_%s %d %d", poolName, totalCost, count)

	// 添加獲得的物品（最多5個）
	for i, item := range result.Items {
		if i >= 5 {
			break
		}
		cmd += " " + item
	}

	// 如果物品不足5個，用空字符串補齊
	for i := len(result.Items); i < 5; i++ {
		cmd += " "
	}

	c.String(http.StatusOK, cmd)
}

// handleQueryAction 處理查詢操作
func (h *UnifiedHandler) handleQueryAction(c *gin.Context, userID string) {
	poolName := c.Query("pool")
	if poolName == "" {
		c.String(http.StatusOK, "/wish_error_text invalid_wish_type")
		return
	}

	mode := c.Query("mode")
	if mode == "" {
		mode = "text"
	}

	// 獲取用戶統計數據
	_, _, currentCount, err := h.service.GetWishStats(userID, poolName)
	if err != nil {
		c.String(http.StatusOK, "/wish_error_text status_query_failed")
		return
	}

	// 獲取祈願券數量
	ticketType := h.getTicketTypeByPool(poolName)
	ticketCount := 0
	if ticketType != "" {
		ticketCount, _ = h.service.GetWishTickets(userID, ticketType)
	}

	if mode == "gui" {
		// GUI模式暫時不實現，返回文本模式
		c.String(http.StatusOK, fmt.Sprintf("/wish_query_result %s %d %d", poolName, currentCount, ticketCount))
	} else {
		// 文本模式
		c.String(http.StatusOK, fmt.Sprintf("/wish_query_result %s %d %d", poolName, currentCount, ticketCount))
	}
}

// getTicketTypeByPool 根據池名獲取對應的祈願券類型
func (h *UnifiedHandler) getTicketTypeByPool(poolName string) string {
	switch poolName {
	case "GlimmeringPrayer":
		return "glimmering_ticket"
	case "StarPath", "StarryMirage":
		return "star_wish_ticket"
	case "OathOfTheRedRose":
		return "red_oath_ticket"
	default:
		return ""
	}
}

// HandleWishStats 處理祈願統計請求（保留，但返回命令格式）
func (h *UnifiedHandler) HandleWishStats(c *gin.Context) {
	userID := c.Query("user")
	if userID == "" {
		c.String(http.StatusOK, "/wish_error_text invalid_user_name")
		return
	}

	poolName := c.Query("pool")
	if poolName == "" {
		c.String(http.StatusOK, "/wish_error_text invalid_wish_type")
		return
	}

	_, _, currentCount, err := h.service.GetWishStats(userID, poolName)
	if err != nil {
		c.String(http.StatusOK, "/wish_error_text status_query_failed")
		return
	}

	// 獲取祈願券數量
	ticketType := h.getTicketTypeByPool(poolName)
	ticketCount := 0
	if ticketType != "" {
		ticketCount, _ = h.service.GetWishTickets(userID, ticketType)
	}

	// 返回統計結果命令
	c.String(http.StatusOK, fmt.Sprintf("/wish_query_result %s %d %d", poolName, currentCount, ticketCount))
}

// HandleWishTickets 處理祈願券相關請求
func (h *UnifiedHandler) HandleWishTickets(c *gin.Context) {
	userID := c.Query("user")
	if userID == "" {
		c.String(http.StatusOK, "/wish_error_text invalid_user_name")
		return
	}

	ticketType := c.Query("type")
	if ticketType == "" {
		c.String(http.StatusOK, "/wish_error_text invalid_ticket_type")
		return
	}

	switch c.Request.Method {
	case "GET":
		// 獲取祈願券數量
		count, err := h.service.GetWishTickets(userID, ticketType)
		if err != nil {
			c.String(http.StatusOK, "/wish_error_text ticket_query_failed")
			return
		}
		// 返回祈願券數量（可能需要特殊的命令格式）
		c.String(http.StatusOK, fmt.Sprintf("/wish_ticket_result %s %d", ticketType, count))

	case "POST":
		// 添加祈願券
		amountStr := c.PostForm("amount")
		if amountStr == "" {
			c.String(http.StatusOK, "/wish_error_text invalid_amount")
			return
		}

		amount, err := strconv.Atoi(amountStr)
		if err != nil {
			c.String(http.StatusOK, "/wish_error_text invalid_amount")
			return
		}

		err = h.service.AddWishTickets(userID, ticketType, amount)
		if err != nil {
			c.String(http.StatusOK, "/wish_error_text ticket_add_failed")
			return
		}

		// 返回成功添加的消息
		c.String(http.StatusOK, fmt.Sprintf("/wish_ticket_added %s %d", ticketType, amount))

	default:
		c.String(http.StatusOK, "/wish_error_text invalid_action")
	}
}

// HandleWishInfo 處理祈願池信息請求
func (h *UnifiedHandler) HandleWishInfo(c *gin.Context) {
	poolName := c.Query("pool")
	if poolName == "" {
		// 返回所有池的信息（可能需要特殊處理）
		c.String(http.StatusOK, "/wish_info_all_pools")
		return
	}

	// 檢查池是否存在
	_, exists := h.service.config.Pools[poolName]
	if !exists {
		c.String(http.StatusOK, "/wish_error_text invalid_wish_type")
		return
	}

	// 如果請求包含用戶信息，添加用戶相關統計
	userID := c.Query("user")
	if userID != "" {
		_, _, currentCount, err := h.service.GetWishStats(userID, poolName)
		if err != nil {
			c.String(http.StatusOK, "/wish_error_text status_query_failed")
			return
		}

		// 獲取祈願券數量
		ticketType := h.getTicketTypeByPool(poolName)
		ticketCount := 0
		if ticketType != "" {
			ticketCount, _ = h.service.GetWishTickets(userID, ticketType)
		}

		// 返回池信息和用戶統計
		c.String(http.StatusOK, fmt.Sprintf("/wish_info_with_stats %s %d %d", poolName, currentCount, ticketCount))
	} else {
		// 返回基本池信息
		c.String(http.StatusOK, fmt.Sprintf("/wish_info_basic %s", poolName))
	}
}
