package wish

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
)

// Handler 祈愿处理器
type Handler struct {
	service *Service
}

// NewHandler 创建祈愿处理器
func NewHandler(service *Service) *Handler {
	return &Handler{
		service: service,
	}
}

// HandleWish 处理祈愿请求 - 兼容原有 PHP 接口
func (h *Handler) HandleWish(c *gin.Context) {
	action := c.Query("action")
	user := c.Query("user")
	wishType := c.DefaultQuery("type", "Starpath")
	valueStr := c.Query("value")

	// 根据 action 路由到不同的处理函数
	switch action {
	case "wish":
		h.handleWishAction(c, user, wishType, valueStr)
	case "query":
		h.handleQueryAction(c, user, wishType)
	case "ticket_add":
		h.handleTicketAddAction(c, user)
	case "ticket_remove":
		h.handleTicketRemoveAction(c, user)
	case "ticket_query":
		h.handleTicketQueryAction(c, user)
	default:
		h.respondError(c, "invalid_action")
	}
}

// handleWishAction 处理祈愿操作
func (h *Handler) handleWishAction(c *gin.Context, user, wishType, valueStr string) {
	// 验证参数
	if user == "" {
		h.respondError(c, "invalid_user_name")
		return
	}

	value, err := strconv.Atoi(valueStr)
	if err != nil || value <= 0 {
		h.respondError(c, "invalid_wish_value")
		return
	}

	// 获取用户余额参数
	balStr := c.Query("bal")
	var userBalance float64 = 0
	if balStr != "" {
		userBalance, err = strconv.ParseFloat(balStr, 64)
		if err != nil {
			h.respondError(c, "invalid_balance")
			return
		}
	}

	// 执行祈愿（包含余额检查）
	result, err := h.service.PerformWishWithBalance(user, wishType, value, userBalance)
	if err != nil {
		h.respondError(c, "wish_failed")
		return
	}

	// 构造响应
	response := h.buildWishResponse(wishType, value, result.Items, result.Cost)
	c.String(http.StatusOK, response)
}

// handleQueryAction 处理查询操作
func (h *Handler) handleQueryAction(c *gin.Context, user, wishType string) {
	if user == "" {
		h.respondError(c, "invalid_user_name")
		return
	}

	mode := c.Query("mode")

	count, err := h.service.QueryWishCount(user, wishType)
	if err != nil {
		h.respondError(c, "query_failed")
		return
	}

	// 查詢可用祈願券數量
	usableTickets, err := h.service.ticketManager.GetUsableTickets(h.service.storage, user, wishType)
	if err != nil {
		h.respondError(c, "ticket_query_failed")
		return
	}
	ticketCount := 0

	for _, v := range usableTickets {
		ticketCount += v
	}

	var response string

	if mode == "gui" {
		response = fmt.Sprintf("/wish_query_gui_result %s %d %d", wishType, count, ticketCount)
	} else {
		response = fmt.Sprintf("/wish_query_result %s %d %d", wishType, count, ticketCount)
	}
	c.String(http.StatusOK, response)
}

// buildWishResponse 构造祈愿响应
func (h *Handler) buildWishResponse(wishType string, count int, items []string, cost int) string {
	// 根据祈愿类型构造不同的响应
	switch wishType {
	case "GlimmeringPrayer":
		return h.buildGlimmeringPrayerResponse(count, items, cost)
	case "Starpath":
		return h.buildStarpathResponse(count, items, cost)
	case "StarryMirage":
		return h.buildStarryMirageResponse(count, items, cost)
	case "OathOfTheRedRose":
		return h.buildOathOfTheRedRoseResponse(count, items, cost)
	default:
		return fmt.Sprintf("/handle_wish_%s %d %d %s", wishType, cost, count, strings.Join(items, " "))
	}
}

// buildGlimmeringPrayerResponse 构造微光之愿祈愿响应
func (h *Handler) buildGlimmeringPrayerResponse(count int, items []string, cost int) string {
	if count == 1 {
		return fmt.Sprintf("/handle_wish_GlimmeringPrayer %d %d %s", cost, count, items[0])
	} else {
		itemsStr := strings.Join(items, " ")
		return fmt.Sprintf("/handle_wish_GlimmeringPrayer %d %d %s", cost, count, itemsStr)
	}
}

// buildStarpathResponse 构造星途祈愿响应
func (h *Handler) buildStarpathResponse(count int, items []string, cost int) string {
	if count == 1 {
		return fmt.Sprintf("/handle_wish_Starpath %d %d %s", cost, count, items[0])
	} else {
		itemsStr := strings.Join(items, " ")
		return fmt.Sprintf("/handle_wish_Starpath %d %d %s", cost, count, itemsStr)
	}
}

// buildStarryMirageResponse 构造星光如梦祈愿响应
func (h *Handler) buildStarryMirageResponse(count int, items []string, cost int) string {
	if count == 1 {
		return fmt.Sprintf("/handle_wish_StarryMirage %d %d %s", cost, count, items[0])
	} else {
		itemsStr := strings.Join(items, " ")
		return fmt.Sprintf("/handle_wish_StarryMirage %d %d %s", cost, count, itemsStr)
	}
}

// buildOathOfTheRedRoseResponse 构造红玫的誓约祈愿响应
func (h *Handler) buildOathOfTheRedRoseResponse(count int, items []string, cost int) string {
	if count == 1 {
		return fmt.Sprintf("/handle_wish_OathOfTheRedRose %d %d %s", cost, count, items[0])
	} else {
		itemsStr := strings.Join(items, " ")
		return fmt.Sprintf("/handle_wish_OathOfTheRedRose %d %d %s", cost, count, itemsStr)
	}
}

// respondError 响应错误
func (h *Handler) respondError(c *gin.Context, errorType string) {
	response := fmt.Sprintf("/wish_error_text %s", errorType)
	c.String(http.StatusOK, response)
}

// handleTicketAddAction 处理祈愿券添加操作
func (h *Handler) handleTicketAddAction(c *gin.Context, user string) {
	if user == "" {
		h.respondError(c, "invalid_user_name")
		return
	}

	ticketType := c.Query("ticket_type")
	amountStr := c.Query("amount")

	if ticketType == "" {
		h.respondError(c, "invalid_ticket_type")
		return
	}

	amount, err := strconv.Atoi(amountStr)
	if err != nil || amount <= 0 {
		h.respondError(c, "invalid_amount")
		return
	}

	// 验证祈愿券类型是否存在
	if _, exists := h.service.ticketManager.GetTicketType(ticketType); !exists {
		h.respondError(c, "unknown_ticket_type")
		return
	}

	// 添加祈愿券
	err = h.service.storage.AddWishTickets(user, ticketType, amount)
	if err != nil {
		h.respondError(c, "ticket_add_failed")
		return
	}

	// 获取添加后的数量
	newAmount, err := h.service.storage.GetWishTickets(user, ticketType)
	if err != nil {
		h.respondError(c, "ticket_query_failed")
		return
	}

	// 返回成功响应
	response := fmt.Sprintf("/ticket_add_result %s %d %d", ticketType, amount, newAmount)
	c.String(http.StatusOK, response)
}

// handleTicketRemoveAction 处理祈愿券删除操作
func (h *Handler) handleTicketRemoveAction(c *gin.Context, user string) {
	if user == "" {
		h.respondError(c, "invalid_user_name")
		return
	}

	ticketType := c.Query("ticket_type")
	amountStr := c.Query("amount")

	if ticketType == "" {
		h.respondError(c, "invalid_ticket_type")
		return
	}

	amount, err := strconv.Atoi(amountStr)
	if err != nil || amount <= 0 {
		h.respondError(c, "invalid_amount")
		return
	}

	// 验证祈愿券类型是否存在
	if _, exists := h.service.ticketManager.GetTicketType(ticketType); !exists {
		h.respondError(c, "unknown_ticket_type")
		return
	}

	// 检查用户当前祈愿券数量
	currentAmount, err := h.service.storage.GetWishTickets(user, ticketType)
	if err != nil {
		h.respondError(c, "ticket_query_failed")
		return
	}

	if currentAmount < amount {
		h.respondError(c, "insufficient_tickets")
		return
	}

	// 删除祈愿券
	err = h.service.storage.AddWishTickets(user, ticketType, -amount)
	if err != nil {
		h.respondError(c, "ticket_remove_failed")
		return
	}

	// 获取删除后的数量
	newAmount, err := h.service.storage.GetWishTickets(user, ticketType)
	if err != nil {
		h.respondError(c, "ticket_query_failed")
		return
	}

	// 返回成功响应
	response := fmt.Sprintf("/ticket_remove_result %s %d %d", ticketType, amount, newAmount)
	c.String(http.StatusOK, response)
}

// handleTicketQueryAction 处理祈愿券查询操作
func (h *Handler) handleTicketQueryAction(c *gin.Context, user string) {
	if user == "" {
		h.respondError(c, "invalid_user_name")
		return
	}

	ticketType := c.Query("ticket_type")

	if ticketType == "" {
		// 查询所有祈愿券
		h.handleTicketQueryAllAction(c, user)
		return
	}

	// 验证祈愿券类型是否存在
	_, exists := h.service.ticketManager.GetTicketType(ticketType)
	if !exists {
		h.respondError(c, "unknown_ticket_type")
		return
	}

	// 查询指定类型的祈愿券数量
	amount, err := h.service.storage.GetWishTickets(user, ticketType)
	if err != nil {
		h.respondError(c, "ticket_query_failed")
		return
	}

	// 返回查询结果 - 只返回券类型和数量
	response := fmt.Sprintf("/ticket_query_result %s %d",
		ticketType, amount)
	c.String(http.StatusOK, response)
}

// handleTicketQueryAllAction 处理查询所有祈愿券操作
func (h *Handler) handleTicketQueryAllAction(c *gin.Context, user string) {
	userData, err := h.service.storage.GetUserData(user)
	if err != nil {
		h.respondError(c, "ticket_query_failed")
		return
	}

	// 构建所有祈愿券的响应
	var ticketList []string

	// 遍历所有已注册的祈愿券类型
	for ticketID := range h.service.ticketManager.TicketTypes {
		amount := 0
		if userData.Wish != nil && userData.Wish.WishTickets != nil {
			amount = userData.Wish.WishTickets[ticketID]
		}

		// 格式: ticketType:amount
		ticketInfo := fmt.Sprintf("%s:%d",
			ticketID, amount)
		ticketList = append(ticketList, ticketInfo)
	}

	// 返回所有祈愿券列表
	response := fmt.Sprintf("/ticket_query_all_result %s", strings.Join(ticketList, " "))
	c.String(http.StatusOK, response)
}
