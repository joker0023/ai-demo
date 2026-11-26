package com.example.demo.service;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class WeatherTool {

//	@McpTool(description = "获取指定城市的实时天气信息")
//	public String getWeather(@McpToolParam(description = "城市名称") String city) {
//		return "{\"city\": \"北京\", \"weather\": \"晴\", \"city\": \"广州\", \"weather\": \"阴\"}";
//	}
	
	@Tool(description = "获取指定城市的实时天气信息")
	public String getWeather(@McpToolParam(description = "城市名称") String city) {
		return "{\"city\": \"北京\", \"weather\": \"晴\", \"city\": \"广州\", \"weather\": \"阴\"}";
	}
	
}
