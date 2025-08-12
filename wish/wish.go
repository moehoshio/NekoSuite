package wish

import (
	wishgo "neko-suite/wish/go"

	"github.com/gin-gonic/gin"
)

// RegisterRoutes 注册祈愿模块的路由
func RegisterRoutes(router *gin.Engine) {
	// 初始化祈愿服务
	service, err := wishgo.NewWishService("wish/config.yaml")
	if err != nil {
		panic("Failed to initialize wish service: " + err.Error())
	}

	// 创建处理器
	handler := wishgo.NewHandler(service)

	// 祈愿模块路由组
	wishGroup := router.Group("/wish")
	{
		// 兼容原有 PHP 接口的路径
		wishGroup.GET("/", handler.HandleWish)
	}
}
