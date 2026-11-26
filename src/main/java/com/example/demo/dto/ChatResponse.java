package com.example.demo.dto;

import lombok.Data;

@Data
public class ChatResponse {

	private String response;
	
	private Integer code;
	
	private String message;
}
