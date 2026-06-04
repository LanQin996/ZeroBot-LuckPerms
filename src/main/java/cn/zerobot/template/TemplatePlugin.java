package cn.zerobot.template;

import cn.zerobot.api.BotContext;
import cn.zerobot.api.BotPlugin;
import cn.zerobot.api.message.MessageSegment;

import java.util.List;
import java.util.Map;

/**
 * ZeroBot 插件模板。
 * <p>
 * 新建插件时可以复制整个 ZeroBotPluginTemplate 项目，然后修改：
 * <ul>
 *     <li>build.gradle.kts 里的 jar 名称</li>
 *     <li>plugin.yml 里的 id/name/version/main</li>
 *     <li>本类的包名、类名和业务逻辑</li>
 * </ul>
 */
public class TemplatePlugin implements BotPlugin {
    private BotContext context;
    private Settings settings;

    /**
     * 插件加载时触发。
     * <p>
     * 通常在这里注册事件监听器、初始化配置、创建定时任务。
     */
    @Override
    public void onLoad(BotContext context) throws Exception {
        this.context = context;
        this.settings = context.loadConfig("config.yml", Settings.class);

        // 只监听群消息。适合写群聊指令、群管、群娱乐功能。
        context.onGroupMessage(event -> {
            String text = event.rawMessage();
            if (text == null) {
                return;
            }

            if (settings.getPingCommand().equals(text)) {
                if (!context.hasPermission(event, settings.getPingPermission(), settings.isPingDefaultAllowed())) {
                    context.reply(event, List.of(MessageSegment.text(settings.getNoPermissionReply())));
                    return;
                }
                context.reply(event, List.of(MessageSegment.text(settings.getPingReply())));
            }
        });

        // 只监听私聊消息。适合写后台管理、私聊菜单、用户绑定等功能。
        context.onPrivateMessage(event -> {
            if ("/help".equals(event.rawMessage())) {
                context.sendPrivateText(event.userId(), "可用命令：/ping");
            }
        });

        // 监听通知事件，例如撤回、戳一戳、群成员变动、群文件上传。
        context.onNotice(event -> {
            context.logger().info("收到通知事件：type={}, raw={}", event.noticeType(), event.raw());
        });

        // 监听请求事件，例如好友申请、加群申请。
        context.onRequest(event -> {
            context.logger().info("收到请求事件：type={}, user={}, flag={}",
                    event.requestType(), event.userId(), event.flag());
        });

        // 兜底监听所有事件。调试新功能时很有用，可以先看 NapCat 实际推送了什么 JSON。
        context.onEvent(event -> {
            context.logger().debug("收到 OneBot 事件：postType={}, raw={}", event.postType(), event.raw());
        });

        context.logger().info("TemplatePlugin 已加载，配置目录：{}", context.configDir());
    }

    /**
     * 插件卸载时触发。
     * <p>
     * 如果插件创建了线程池、定时任务、文件句柄、数据库连接，应该在这里释放。
     */
    @Override
    public void onUnload() {
        if (context != null) {
            context.logger().info("TemplatePlugin 已卸载");
        }
    }

    /**
     * 示例：调用还没有被 ZeroBot 封装的 OneBot 动作。
     */
    private void exampleRawAction(String groupId) {
        context.callAction("get_group_info", Map.of("group_id", groupId))
                .thenAccept(response -> context.logger().info("群信息：{}", response.data()))
                .exceptionally(error -> {
                    context.logger().warn("获取群信息失败", error);
                    return null;
                });
    }

    /**
     * 插件配置。
     * <p>
     * 首次加载插件时，ZeroBot 会自动生成：
     * config/<插件ID>/config.yml
     */
    public static class Settings {
        private String pingCommand = "/ping";
        private String pingReply = "pong";
        private String pingPermission = "template.ping";
        private boolean pingDefaultAllowed = true;
        private String noPermissionReply = "你没有权限使用这个命令";

        public String getPingCommand() {
            return pingCommand;
        }

        public void setPingCommand(String pingCommand) {
            this.pingCommand = pingCommand;
        }

        public String getPingReply() {
            return pingReply;
        }

        public void setPingReply(String pingReply) {
            this.pingReply = pingReply;
        }

        public String getPingPermission() {
            return pingPermission;
        }

        public void setPingPermission(String pingPermission) {
            this.pingPermission = pingPermission;
        }

        public boolean isPingDefaultAllowed() {
            return pingDefaultAllowed;
        }

        public void setPingDefaultAllowed(boolean pingDefaultAllowed) {
            this.pingDefaultAllowed = pingDefaultAllowed;
        }

        public String getNoPermissionReply() {
            return noPermissionReply;
        }

        public void setNoPermissionReply(String noPermissionReply) {
            this.noPermissionReply = noPermissionReply;
        }
    }
}
