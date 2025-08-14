package event

import (
	"log"

	"github.com/gin-gonic/gin"

	event "neko-suite/event/go"
	common "neko-suite/go"
)

// RegisterRoutes 註冊 event 模塊的路由
func RegisterRoutes(router *gin.Engine) {
	// 加载配置
	config, err := event.LoadConfig("event/config.yaml")
	if err != nil {
		log.Fatal("加载 event 配置失败:", err)
	}

	// 創建統一存儲
	commonStorage, err := common.NewStorage(config.Storage)
	if err != nil {
		log.Fatal("创建 event 存储失败:", err)
	}

	// 创建存储适配器
	storage := event.NewEventStorageAdapter(commonStorage)

	// 创建服务
	service, err := event.NewUnifiedEventService(config, storage)
	if err != nil {
		log.Fatal("创建 event 服务失败:", err)
	}

	// 创建处理器
	handler := event.NewHandler(service)

	// 註冊路由
	eventRoutes := router.Group("/event")
	{
		eventRoutes.GET("/", handler.HandleEvent)
		eventRoutes.POST("/", handler.HandleEvent)
	}
}
