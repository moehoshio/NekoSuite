package wish

import (
	wishgo "neko-suite/wish/go"

	"github.com/gin-gonic/gin"
)

// RegisterRoutes 注册祈愿模块的路由
func RegisterRoutes(router *gin.Engine) {
	// 初始化統一祈願服務
	service, err := wishgo.NewUnifiedWishService("wish/config.yaml")
	if err != nil {
		panic("Failed to initialize unified wish service: " + err.Error())
	}

	// 创建处理器（暂时使用原有的处理器结构）
	handler := wishgo.NewUnifiedHandler(service)

	// 祈愿模块路由组
	wishGroup := router.Group("/wish")
	{
		wishGroup.GET("/", handler.HandleWish)
		wishGroup.POST("/", handler.HandleWish)
	}
}
