package com.flowforge.app

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 这是前后端分开开发时使用的跨域配置。
 *
 * 当前前端运行在 Vite 开发服务器上，地址通常是 5173 端口。
 * 浏览器会对跨域请求做安全检查，所以这里显式放行本地管理台地址。
 *
 * 这里先只放行本地开发域名，避免为了联调直接把所有来源都开放掉。
 */
@Configuration
class WebCorsConfiguration : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(
                "http://127.0.0.1:5173",
                "http://localhost:5173"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600)
    }
}
