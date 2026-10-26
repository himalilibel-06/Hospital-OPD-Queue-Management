package com.smartopd;

import com.smartopd.service.QueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * SmartOpdApplication - Entry point for the SmartOPD Spring Boot application.
 * 
 * ============================================================================
 * OOP & Architectural Concepts:
 * - Layered Architecture: Clean separation of Controller, Service, and DAO layers.
 * - Inversion of Control (IoC): Spring Boot initializes and manages components.
 * - Embedded Web Server: Starts embedded Tomcat on port 8080 serving REST endpoints
 *   and hosting the existing pure-JS/HTML frontend statically.
 * ============================================================================
 */
@SpringBootApplication
public class SmartOpdApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartOpdApplication.class, args);
    }

    @Bean
    public ApplicationRunner startupBanner(QueueService queueService, @Value("${server.port:8080}") int port) {
        return args -> {
            String dbPath = queueService.getConnectionProvider().getDbPath();
            String absoluteDbPath = new File(dbPath).getAbsolutePath();
            String lanIp = getLanIpAddress();

            System.out.println();
            System.out.println("================================================================================");
            System.out.println("  SmartOPD – Hospital OPD Queue Management System");
            System.out.println("================================================================================");
            System.out.println("  Database File:    " + absoluteDbPath);
            System.out.println("  Local Web URL:    http://localhost:" + port + "/login.html");
            if (lanIp != null && !lanIp.isBlank()) {
                System.out.println("  LAN Mobile URL:   http://" + lanIp + ":" + port + "/login.html");
                System.out.println("  Mobile Tracker:   http://" + lanIp + ":" + port + "/patient-tracker.html?guest=true");
            }
            System.out.println("  Display Board:    http://localhost:" + port + "/display-board.html");
            System.out.println("================================================================================");
            System.out.println();
        };
    }

    private static String getLanIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
