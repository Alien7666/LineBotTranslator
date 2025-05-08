package com.linetranslate.bot.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linetranslate.bot.service.AdminService;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理員命令控制器
 * 處理所有管理員相關的命令
 */
@Component
@Slf4j
public class AdminController {

    private final AdminService adminService;

    @Autowired
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 處理管理員命令
     * 
     * @param userId 用戶ID
     * @param command 命令內容
     * @return 回應消息
     */
    public Message handleCommand(String userId, String command) {
        log.info("處理管理員命令: 用戶={}, 命令={}", userId, command);
        
        if (!adminService.isAdmin(userId)) {
            return new TextMessage("您沒有管理員權限。");
        }
        
        if (command.isEmpty()) {
            return new TextMessage(getAdminHelpMessage());
        }
        
        String[] parts = command.split(" ", 2);
        String subCommand = parts[0].toLowerCase();
        String param = parts.length > 1 ? parts[1] : "";
        
        switch (subCommand) {
            case "broadcast":
                if (param.isEmpty()) {
                    return new TextMessage("請指定要廣播的消息。例如：/admin broadcast 您好，這是一條廣播消息。");
                }
                return handleBroadcastCommand(userId, param);
                
            case "stats":
                // 用現有的 getSystemStats 方法
                return new TextMessage(adminService.getSystemStats());
                
            case "users":
                return handleUsersCommand(userId);
                
            case "user":
                if (param.isEmpty()) {
                    return new TextMessage("請指定要查詢的用戶ID。例如：/admin user U123456789");
                }
                return handleUserCommand(userId, param);
                
            case "config":
                // 將在未來實現
                return new TextMessage("⚙️ 系統配置信息功能尚未實現\n\n此功能將顯示系統的各項配置設定。");
                
            case "usage":
                // 將在未來實現
                return new TextMessage("💰 API 使用量和費用功能尚未實現\n\n此功能將顯示 API 的使用量和相關費用。");
                
            case "today":
                // 使用現有的 getTodayStats 方法
                return new TextMessage(adminService.getTodayStats());
                
            default:
                return new TextMessage("未知的管理員命令：" + subCommand + "\n\n" + getAdminHelpMessage());
        }
    }
    
    /**
     * 處理廣播命令
     */
    private Message handleBroadcastCommand(String userId, String message) {
        log.info("廣播命令: 用戶={}, 消息={}", userId, message);
        
        try {
            int count = adminService.broadcastMessage(message);
            return new TextMessage("📢 廣播成功\n\n已向 " + count + " 個用戶發送消息：\n" + message);
        } catch (Exception e) {
            log.error("廣播消息失敗", e);
            return new TextMessage("廣播消息失敗：" + e.getMessage());
        }
    }
    
    /**
     * 處理用戶列表命令
     */
    private Message handleUsersCommand(String userId) {
        log.info("用戶列表命令: 用戶={}", userId);
        
        try {
            List<Map<String, Object>> users = adminService.getRecentUsers(10);
            StringBuilder sb = new StringBuilder();
            sb.append("👥 最近活躍用戶\n\n");
            
            for (int i = 0; i < users.size(); i++) {
                Map<String, Object> user = users.get(i);
                sb.append(i + 1).append(". ");
                sb.append(user.get("displayName")).append(" (");
                sb.append(user.get("userId").toString().substring(0, 6)).append("...)\n");
                sb.append("   最後活動：").append(user.get("lastActiveTime")).append("\n");
            }
            
            sb.append("\n使用 /admin user [ID] 查看用戶詳細信息");
            
            return new TextMessage(sb.toString());
        } catch (Exception e) {
            log.error("獲取用戶列表失敗", e);
            return new TextMessage("獲取用戶列表失敗：" + e.getMessage());
        }
    }
    
    /**
     * 處理查詢用戶命令
     */
    private Message handleUserCommand(String userId, String targetUserId) {
        log.info("查詢用戶命令: 管理員={}, 目標用戶={}", userId, targetUserId);
        
        try {
            Map<String, Object> userInfo = adminService.getUserInfo(targetUserId);
            if (userInfo == null) {
                return new TextMessage("找不到用戶：" + targetUserId);
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("👤 用戶詳細信息\n\n");
            sb.append("• 用戶ID：").append(userInfo.get("userId")).append("\n");
            sb.append("• 顯示名稱：").append(userInfo.get("displayName")).append("\n");
            sb.append("• 註冊時間：").append(userInfo.get("registrationTime")).append("\n");
            sb.append("• 最後活動：").append(userInfo.get("lastActiveTime")).append("\n\n");
            
            sb.append("📊 使用統計\n");
            sb.append("• 翻譯次數：").append(userInfo.get("translationCount")).append("\n");
            sb.append("• 圖片翻譯次數：").append(userInfo.get("imageTranslationCount")).append("\n\n");
            
            sb.append("⚙️ 用戶設置\n");
            sb.append("• 預設翻譯語言：").append(userInfo.get("preferredLanguage")).append("\n");
            sb.append("• 中文翻譯目標語言：").append(userInfo.get("preferredChineseTargetLanguage")).append("\n");
            sb.append("• 預設 AI 提供者：").append(userInfo.get("preferredAiProvider")).append("\n");
            
            return new TextMessage(sb.toString());
        } catch (Exception e) {
            log.error("獲取用戶信息失敗", e);
            return new TextMessage("獲取用戶信息失敗：" + e.getMessage());
        }
    }
    
    /**
     * 獲取管理員幫助信息
     */
    private String getAdminHelpMessage() {
        return "🔐 LINE 翻譯機器人管理員命令 🔐\n\n" +
                "【💻 管理員命令列表】\n" +
                "📖 /adminhelp - 顯示管理員幫助信息\n" +
                "📢 /admin broadcast [消息] - 向所有用戶廣播消息\n" +
                "📊 /admin stats - 查看系統統計信息\n" +
                "🔍 /admin users - 查看用戶列表\n" +
                "🔎 /admin user [用戶ID] - 查看指定用戶詳細信息\n" +
                "🔧 /admin config - 查看系統配置\n" +
                "💰 /admin usage - 查看 API 使用量和費用\n" +
                "📅 /admin today - 查看今日統計信息";
    }
}
