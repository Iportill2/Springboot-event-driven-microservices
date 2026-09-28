package com.ecommerce.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// El escaneo se limita a com.ecommerce.chat a proposito. JwtService, que vive
// en shared/common, es un @Component que exige app.jwt.secret: si se escaneara
// "com.ecommerce" este servicio arrancaria sin esa propiedad, y ademas el
// servicio de chat no valida tokens porque lo hace el gateway.
@SpringBootApplication(scanBasePackages = "com.ecommerce.chat")
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
