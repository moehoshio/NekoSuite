package event

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

// Handler 活動處理器
type Handler struct {
	service *UnifiedEventService
}

// NewHandler 創建活動處理器
func NewHandler(service *UnifiedEventService) *Handler {
	return &Handler{
		service: service,
	}
}

// HandleEvent 處理活動相關請求
func (h *Handler) HandleEvent(c *gin.Context) {
	// 獲取用戶名
	user := c.Query("user")
	if user == "" {
		c.String(http.StatusOK, "/event_error_text invalid_user_name")
		return
	}

	// 獲取操作類型
	action := c.Query("action")
	if action == "" {
		c.String(http.StatusOK, "/event_error_text invalid_action")
		return
	}

	// 根據操作類型分發處理
	switch action {
	case "check":
		h.handleCheckAction(c, user)
	case "participate":
		h.handleParticipateAction(c, user)
	case "claim":
		h.handleClaimAction(c, user)
	case "status":
		h.handleStatusAction(c, user)
	case "list":
		h.handleListAction(c, user)
	default:
		c.String(http.StatusOK, "/event_error_text unknown_action")
	}
}

// handleCheckAction 處理檢查活動操作
func (h *Handler) handleCheckAction(c *gin.Context, user string) {
	if user == "" {
		c.String(http.StatusOK, "/event_error_text invalid_user_name")
		return
	}

	// 獲取所有可用活動（簡化實現）
	availableEvents := make(map[string]*EventResult)
	for eventID, config := range h.service.config.Events {
		// 這裡應該有邏輯檢查用戶是否可以參與該活動
		// 暫時返回所有活動作為可用活動
		result := &EventResult{
			EventID:   eventID,
			EventType: config.Type,
			Success:   true,
			Message:   config.Description,
			Commands:  []string{}, // 空命令列表
		}
		availableEvents[eventID] = result
	}

	// 構造響應
	if len(availableEvents) == 0 {
		// 沒有可用活動
		c.String(http.StatusOK, "/event_error_text no_available_events")
		return
	}

	// 有可用活動，自動發放獎勵（如果配置了自動領取）
	var commands []string

	for eventID, result := range availableEvents {
		if result.Success && len(result.Commands) > 0 {
			// 添加獎勵發放命令
			commands = append(commands, result.Commands...)

			// 執行參與操作（標記為已參與）
			_, err := h.service.ProcessEvent(user, eventID)
			if err != nil {
				// 記錄錯誤但不中斷流程
				continue
			}
		}
	}

	// 如果有命令需要執行，返回命令
	if len(commands) > 0 {
		// 返回第一個命令，MyCommand 會處理多個命令的情況
		c.String(http.StatusOK, commands[0])
	} else {
		// 沒有需要執行的命令
		c.String(http.StatusOK, "/event_error_text no_commands_to_execute")
	}
}

// handleParticipateAction 處理參與活動操作
func (h *Handler) handleParticipateAction(c *gin.Context, user string) {
	// 獲取活動ID
	eventID := c.Query("event_id")
	if eventID == "" {
		c.String(http.StatusOK, "/event_error_text invalid_event_id")
		return
	}

	// 處理活動參與
	result, err := h.service.ProcessEvent(user, eventID)
	if err != nil {
		c.String(http.StatusOK, "/event_error_text participation_failed")
		return
	}

	// 如果有命令需要執行，返回第一個命令
	if len(result.Commands) > 0 {
		c.String(http.StatusOK, result.Commands[0])
	} else {
		// 參與成功但沒有特殊命令
		c.String(http.StatusOK, "/event_participate_success")
	}
}

// handleClaimAction 處理領取獎勵操作
func (h *Handler) handleClaimAction(c *gin.Context, user string) {
	// 獲取活動ID
	eventID := c.Query("event_id")
	if eventID == "" {
		c.String(http.StatusOK, "/event_error_text invalid_event_id")
		return
	}

	// 處理獎勵領取
	result, err := h.service.ProcessEvent(user, eventID)
	if err != nil {
		c.String(http.StatusOK, "/event_error_text claim_failed")
		return
	}

	// 如果有命令需要執行，返回第一個命令
	if len(result.Commands) > 0 {
		c.String(http.StatusOK, result.Commands[0])
	} else {
		// 領取成功但沒有特殊命令
		c.String(http.StatusOK, "/event_claim_success")
	}
}

// handleStatusAction 處理查詢活動狀態操作
func (h *Handler) handleStatusAction(c *gin.Context, user string) {
	// 獲取活動ID
	eventID := c.Query("event_id")
	if eventID == "" {
		c.String(http.StatusOK, "/event_error_text invalid_event_id")
		return
	}

	// 查詢活動狀態
	result, err := h.service.ProcessEvent(user, eventID)
	if err != nil {
		c.String(http.StatusOK, "/event_error_text status_query_failed")
		return
	}

	// 返回狀態查詢命令
	if result.Success {
		c.String(http.StatusOK, "/event_status_success")
	} else {
		c.String(http.StatusOK, "/event_status_failed")
	}
}

// handleListAction 處理列出所有活動操作
func (h *Handler) handleListAction(c *gin.Context, user string) {
	// 返回顯示活動列表的命令
	c.String(http.StatusOK, "/event_show_list "+user)
}
