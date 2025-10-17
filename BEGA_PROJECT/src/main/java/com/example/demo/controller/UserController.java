package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.demo.dto.UserDto;
import com.example.demo.service.UserService;

@Controller
@ResponseBody
public class UserController {
    
    private final UserService userService;

    public UserController(UserService userService) {
        
        this.userService = userService;
    }

    @PostMapping("/join")
    public String joinProcess(UserDto userDTO) {

        System.out.println(userDTO.getUsername());
        userService.joinProcess(userDTO);

        return "ok";
    }
}