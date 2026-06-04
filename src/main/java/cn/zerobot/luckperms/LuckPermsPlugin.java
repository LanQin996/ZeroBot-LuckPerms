package cn.zerobot.luckperms;

import cn.zerobot.api.BotContext;
import cn.zerobot.api.BotPlugin;
import cn.zerobot.api.event.MessageEvent;
import cn.zerobot.api.message.MessageSegment;
import cn.zerobot.api.permission.PermissionService;
import cn.zerobot.api.permission.PermissionSubject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LuckPermsPlugin implements BotPlugin {
    private static final List<String> BUILT_IN_COMMAND_PREFIXES = List.of(
            "/lp",
            "/luckperms"
    );

    private BotContext context;
    private Settings settings;
    private PermissionRepository repository;

    @Override
    public void onLoad(BotContext context) throws Exception {
        this.context = context;
        this.settings = context.loadConfig("config.yml", Settings.class);
        this.repository = new PermissionRepository(context.dataDir().resolve(settings.getDataFile()), settings);
        repository.loadOrCreate();

        PermissionService service = new LuckPermsPermissionService(context.permission(), repository);
        context.registerPermissionService(service);
        context.onMessage(event -> {
            if (event instanceof MessageEvent messageEvent) {
                handleMessage(messageEvent);
            }
        });

        context.logger().info("ZeroBot LuckPerms 已加载，数据文件：{}", repository.file());
    }

    @Override
    public void onUnload() throws Exception {
        if (repository != null) {
            repository.save();
        }
        if (context != null) {
            context.logger().info("ZeroBot LuckPerms 已卸载");
        }
    }

    private void handleMessage(MessageEvent event) throws Exception {
        ParsedCommand command = parseCommand(event.rawMessage());
        if (command == null) {
            return;
        }
        if (!settings.getAdminPermission().isBlank()
                && !context.hasPermission(event, settings.getAdminPermission(), false)) {
            reply(event, settings.getNoPermissionReply());
            return;
        }

        List<String> args = command.args();
        if (args.isEmpty() || "help".equalsIgnoreCase(args.get(0))) {
            reply(event, helpText());
            return;
        }

        try {
            String root = args.get(0).toLowerCase(Locale.ROOT);
            if (isAny(root, "reload", "rl")) {
                repository.loadOrCreate();
                reply(event, "LuckPerms 数据已重新加载。");
            } else if (isAny(root, "save")) {
                repository.save();
                reply(event, "LuckPerms 数据已保存。");
            } else if (isAny(root, "groups", "listgroups", "lg")) {
                reply(event, repository.formatGroups());
            } else if (isAny(root, "creategroup", "cg")) {
                reply(event, handleCreateGroup(args));
            } else if (isAny(root, "deletegroup", "delgroup", "delete-group", "dg")) {
                reply(event, handleDeleteGroup(args));
            } else if (isAny(root, "check", "test", "has", "haspermission", "hasperm")) {
                reply(event, handleCheck(event, args));
            } else if (isAny(root, "group", "g")) {
                reply(event, handleGroup(args));
            } else if (isAny(root, "user", "u")) {
                reply(event, handleUser(event, args));
            } else {
                reply(event, "未知命令。发送 " + command.prefix() + " help 查看可用命令。");
            }
        } catch (CommandException e) {
            reply(event, e.getMessage());
        } catch (IOException e) {
            context.logger().warn("LuckPerms data operation failed", e);
            reply(event, "LuckPerms 数据读写失败，请查看控制台日志。");
        }
    }

    private String handleCheck(MessageEvent event, List<String> args) throws CommandException {
        if (args.size() < 3) {
            throw new CommandException("用法：check <QQ> <权限节点> [群号]");
        }
        String userId = resolveUserId(event, args.get(1));
        String permission = args.get(2);
        String groupId = args.size() >= 4 ? args.get(3) : null;
        PermissionSubject subject = new PermissionSubject(userId, groupId, groupId == null ? "private" : "group");
        PermissionDecision decision = repository.resolve(subject, permission);
        boolean allowed = context.permission().hasPermission(subject, permission, false);
        if (decision == null) {
            return "检查结果：" + userId + " -> " + permission + " = " + allowed + "\n来源：无显式节点";
        }
        return "检查结果：" + userId + " -> " + permission + " = " + allowed + "\n来源：" + decision.source();
    }

    private String handleCreateGroup(List<String> args) throws CommandException, IOException {
        if (args.size() < 2) {
            throw new CommandException("用法：creategroup <组名> [权重]");
        }
        String groupName = args.get(1);
        int weight = args.size() >= 3 ? parseInt(args.get(2), "权重必须是整数。") : 0;
        return repository.createGroup(groupName, weight)
                ? "权限组已创建：" + normalizeName(groupName)
                : "权限组已存在：" + normalizeName(groupName);
    }

    private String handleDeleteGroup(List<String> args) throws CommandException, IOException {
        if (args.size() < 2) {
            throw new CommandException("用法：deletegroup <组名>");
        }
        String groupName = args.get(1);
        return repository.deleteGroup(groupName)
                ? "权限组已删除：" + normalizeName(groupName)
                : "权限组不存在，或默认组不能删除：" + normalizeName(groupName);
    }

    private String handleGroup(List<String> args) throws CommandException, IOException {
        if (args.size() < 2) {
            throw new CommandException("用法：group <组名> <create|delete|info|weight|permission|parent>");
        }
        String groupName = args.get(1);
        if (args.size() == 2 || isAny(args.get(2), "info", "list", "show", "display")) {
            return repository.formatGroup(groupName);
        }

        String action = args.get(2).toLowerCase(Locale.ROOT);
        switch (action) {
            case "create", "make", "add" -> {
                int weight = args.size() >= 4 ? parseInt(args.get(3), "权重必须是整数。") : 0;
                return repository.createGroup(groupName, weight)
                        ? "权限组已创建：" + normalizeName(groupName)
                        : "权限组已存在：" + normalizeName(groupName);
            }
            case "delete", "del", "remove", "rm" -> {
                return repository.deleteGroup(groupName)
                        ? "权限组已删除：" + normalizeName(groupName)
                        : "权限组不存在，或默认组不能删除：" + normalizeName(groupName);
            }
            case "weight", "w", "setweight" -> {
                if (args.size() < 4) {
                    throw new CommandException("用法：group <组名> weight <整数>");
                }
                int weight = parseInt(args.get(3), "权重必须是整数。");
                repository.setGroupWeight(groupName, weight);
                return "权限组权重已更新：" + normalizeName(groupName) + " = " + weight;
            }
            case "permission", "permissions", "perm", "perms", "p" -> {
                if (args.size() < 5) {
                    throw new CommandException("用法：group <组名> permission <set|unset> <权限节点> [true|false]");
                }
                String sub = args.get(3).toLowerCase(Locale.ROOT);
                String node = args.get(4);
                if (isAny(sub, "set", "add", "give", "grant", "allow")) {
                    boolean value = args.size() >= 6 ? parseBoolean(args.get(5)) : true;
                    repository.setGroupPermission(groupName, node, value);
                    return "权限节点已设置：group " + normalizeName(groupName) + " " + normalizePermission(node) + " = " + value;
                }
                if (isAny(sub, "unset", "remove", "delete", "del", "rm", "clear")) {
                    repository.unsetGroupPermission(groupName, node);
                    return "权限节点已移除：group " + normalizeName(groupName) + " " + normalizePermission(node);
                }
                throw new CommandException("用法：group <组名> permission <set|unset> <权限节点> [true|false]");
            }
            case "parent", "parents", "inheritance", "inherits" -> {
                if (args.size() < 5) {
                    throw new CommandException("用法：group <组名> parent <add|remove> <父组>");
                }
                String sub = args.get(3).toLowerCase(Locale.ROOT);
                String parent = args.get(4);
                if (isAny(sub, "add", "set", "give", "grant")) {
                    repository.addGroupParent(groupName, parent);
                    return "继承关系已添加：" + normalizeName(groupName) + " -> " + normalizeName(parent);
                }
                if (isAny(sub, "remove", "delete", "del", "rm", "unset", "clear")) {
                    repository.removeGroupParent(groupName, parent);
                    return "继承关系已移除：" + normalizeName(groupName) + " -/-> " + normalizeName(parent);
                }
                throw new CommandException("用法：group <组名> parent <add|remove> <父组>");
            }
            default -> throw new CommandException("未知 group 子命令。");
        }
    }

    private String handleUser(MessageEvent event, List<String> args) throws CommandException, IOException {
        if (args.size() < 2) {
            throw new CommandException("用法：user <QQ> <info|permission|parent>");
        }
        String userId = resolveUserId(event, args.get(1));
        if (args.size() == 2 || isAny(args.get(2), "info", "list", "show", "display")) {
            return repository.formatUser(userId);
        }

        String action = args.get(2).toLowerCase(Locale.ROOT);
        switch (action) {
            case "permission", "permissions", "perm", "perms", "p" -> {
                if (args.size() < 5) {
                    throw new CommandException("用法：user <QQ> permission <set|unset> <权限节点> [true|false]");
                }
                String sub = args.get(3).toLowerCase(Locale.ROOT);
                String node = args.get(4);
                if (isAny(sub, "set", "add", "give", "grant", "allow")) {
                    boolean value = args.size() >= 6 ? parseBoolean(args.get(5)) : true;
                    repository.setUserPermission(userId, node, value);
                    return "用户权限已设置：" + userId + " " + normalizePermission(node) + " = " + value;
                }
                if (isAny(sub, "unset", "remove", "delete", "del", "rm", "clear")) {
                    repository.unsetUserPermission(userId, node);
                    return "用户权限已移除：" + userId + " " + normalizePermission(node);
                }
                throw new CommandException("用法：user <QQ> permission <set|unset> <权限节点> [true|false]");
            }
            case "parent", "parents", "inheritance", "inherits", "group", "groups" -> {
                if (args.size() < 5) {
                    throw new CommandException("用法：user <QQ> parent <add|remove> <组名>");
                }
                String sub = args.get(3).toLowerCase(Locale.ROOT);
                String groupName = args.get(4);
                if (isAny(sub, "add", "set", "give", "grant")) {
                    repository.addUserGroup(userId, groupName);
                    return "用户已加入权限组：" + userId + " -> " + normalizeName(groupName);
                }
                if (isAny(sub, "remove", "delete", "del", "rm", "unset", "clear")) {
                    repository.removeUserGroup(userId, groupName);
                    return "用户已移出权限组：" + userId + " -/-> " + normalizeName(groupName);
                }
                throw new CommandException("用法：user <QQ> parent <add|remove> <组名>");
            }
            default -> throw new CommandException("未知 user 子命令。");
        }
    }

    private ParsedCommand parseCommand(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> tokens = tokenize(raw.trim());
        if (tokens.isEmpty()) {
            return null;
        }
        String first = tokens.get(0).toLowerCase(Locale.ROOT);
        for (String prefix : settings.getCommandPrefixes()) {
            String normalizedPrefix = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
            if (!normalizedPrefix.isEmpty() && first.equals(normalizedPrefix)) {
                return new ParsedCommand(prefix.trim(), tokens.subList(1, tokens.size()));
            }
        }
        return null;
    }

    private String resolveUserId(MessageEvent event, String value) throws CommandException {
        String userId = event.resolveUserId(value);
        if (userId == null) {
            throw new CommandException("无法识别用户，请使用 QQ 号或 @用户。");
        }
        return userId;
    }

    private boolean isAny(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (quoted) {
                if (ch == quote) {
                    quoted = false;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quoted = true;
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private int parseInt(String value, String errorMessage) throws CommandException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CommandException(errorMessage);
        }
    }

    private boolean parseBoolean(String value) throws CommandException {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "allow", "yes", "1", "on" -> true;
            case "false", "deny", "no", "0", "off" -> false;
            default -> throw new CommandException("布尔值只能是 true/false、allow/deny、yes/no。");
        };
    }

    private void reply(MessageEvent event, String text) {
        context.reply(event, List.of(MessageSegment.text(text)));
    }

    private String helpText() {
        return """
                ZeroBot LuckPerms
                /lp groups
                /lp group <组名> create [权重]
                /lp group <组名> permission set <节点> [true|false]
                /lp group <组名> parent add <父组>
                /lp user <QQ> parent add <组名>
                /lp user <QQ> permission set <节点> [true|false]
                /lp check <QQ> <节点> [群号]
                /lp reload
                """.strip();
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePermission(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ParsedCommand(String prefix, List<String> args) {
    }

    private record PermissionDecision(boolean value, String source) {
    }

    private static class CommandException extends Exception {
        CommandException(String message) {
            super(message);
        }
    }

    public static class Settings {
        private List<String> commandPrefixes = new ArrayList<>(BUILT_IN_COMMAND_PREFIXES);
        private String adminPermission = "luckperms.admin";
        private String noPermissionReply = "你没有权限使用 LuckPerms 命令。";
        private String dataFile = "permissions.yml";
        private String defaultGroup = "default";
        private boolean createAdminGroup = true;

        public List<String> getCommandPrefixes() {
            return commandPrefixes;
        }

        public void setCommandPrefixes(List<String> commandPrefixes) {
            this.commandPrefixes = commandPrefixes == null ? new ArrayList<>() : commandPrefixes;
        }

        public String getAdminPermission() {
            return adminPermission == null ? "" : adminPermission;
        }

        public void setAdminPermission(String adminPermission) {
            this.adminPermission = adminPermission == null ? "" : adminPermission;
        }

        public String getNoPermissionReply() {
            return noPermissionReply == null ? "" : noPermissionReply;
        }

        public void setNoPermissionReply(String noPermissionReply) {
            this.noPermissionReply = noPermissionReply == null ? "" : noPermissionReply;
        }

        public String getDataFile() {
            return dataFile == null || dataFile.isBlank() ? "permissions.yml" : dataFile;
        }

        public void setDataFile(String dataFile) {
            this.dataFile = dataFile;
        }

        public String getDefaultGroup() {
            return defaultGroup == null || defaultGroup.isBlank() ? "default" : normalizeName(defaultGroup);
        }

        public void setDefaultGroup(String defaultGroup) {
            this.defaultGroup = defaultGroup;
        }

        public boolean isCreateAdminGroup() {
            return createAdminGroup;
        }

        public void setCreateAdminGroup(boolean createAdminGroup) {
            this.createAdminGroup = createAdminGroup;
        }
    }

    private static class LuckPermsPermissionService implements PermissionService {
        private final PermissionService fallback;
        private final PermissionRepository repository;

        LuckPermsPermissionService(PermissionService fallback, PermissionRepository repository) {
            this.fallback = Objects.requireNonNull(fallback, "fallback");
            this.repository = Objects.requireNonNull(repository, "repository");
        }

        @Override
        public boolean hasPermission(PermissionSubject subject, String permission) {
            if (permission == null || permission.isBlank()) {
                return true;
            }
            if (fallback.hasPermission(subject, permission)) {
                return true;
            }
            PermissionDecision decision = repository.resolve(subject, permission);
            return decision != null && decision.value();
        }

        @Override
        public boolean hasPermission(PermissionSubject subject, String permission, boolean defaultAllowed) {
            if (permission == null || permission.isBlank()) {
                return true;
            }
            if (fallback.hasPermission(subject, permission)) {
                return true;
            }
            PermissionDecision decision = repository.resolve(subject, permission);
            return decision == null ? defaultAllowed : decision.value();
        }
    }

    private static class PermissionRepository {
        private final Path file;
        private final Settings settings;
        private final ObjectMapper mapper;
        private PermissionStore store = new PermissionStore();

        PermissionRepository(Path file, Settings settings) {
            this.file = file.toAbsolutePath().normalize();
            this.settings = settings;
            YAMLFactory factory = YAMLFactory.builder()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .build();
            this.mapper = new ObjectMapper(factory);
        }

        Path file() {
            return file;
        }

        synchronized void loadOrCreate() throws IOException {
            if (Files.notExists(file)) {
                store = new PermissionStore();
                ensureDefaults();
                save();
                return;
            }
            PermissionStore loaded = mapper.readValue(file.toFile(), PermissionStore.class);
            store = loaded == null ? new PermissionStore() : loaded;
            ensureDefaults();
        }

        synchronized void save() throws IOException {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), store);
        }

        synchronized boolean createGroup(String name, int weight) throws IOException, CommandException {
            String key = requireName(name);
            if (store.groups.containsKey(key)) {
                return false;
            }
            PermissionGroup group = new PermissionGroup();
            group.name = key;
            group.weight = weight;
            store.groups.put(key, group);
            save();
            return true;
        }

        synchronized boolean deleteGroup(String name) throws IOException, CommandException {
            String key = requireName(name);
            if (key.equals(store.defaultGroup) || store.groups.remove(key) == null) {
                return false;
            }
            for (PermissionGroup group : store.groups.values()) {
                group.parents.remove(key);
            }
            for (PermissionUser user : store.users.values()) {
                user.groups.remove(key);
            }
            save();
            return true;
        }

        synchronized void setGroupWeight(String name, int weight) throws IOException, CommandException {
            group(name).weight = weight;
            save();
        }

        synchronized void setGroupPermission(String name, String permission, boolean value) throws IOException, CommandException {
            group(name).permissions.put(requirePermission(permission), value);
            save();
        }

        synchronized void unsetGroupPermission(String name, String permission) throws IOException, CommandException {
            group(name).permissions.remove(requirePermission(permission));
            save();
        }

        synchronized void addGroupParent(String name, String parent) throws IOException, CommandException {
            PermissionGroup group = group(name);
            String parentKey = requireName(parent);
            if (group.name.equals(parentKey)) {
                throw new CommandException("权限组不能继承自己。");
            }
            group(parentKey);
            group.parents.add(parentKey);
            save();
        }

        synchronized void removeGroupParent(String name, String parent) throws IOException, CommandException {
            group(name).parents.remove(requireName(parent));
            save();
        }

        synchronized void setUserPermission(String userId, String permission, boolean value) throws IOException, CommandException {
            user(userId).permissions.put(requirePermission(permission), value);
            save();
        }

        synchronized void unsetUserPermission(String userId, String permission) throws IOException, CommandException {
            user(userId).permissions.remove(requirePermission(permission));
            save();
        }

        synchronized void addUserGroup(String userId, String groupName) throws IOException, CommandException {
            String groupKey = requireName(groupName);
            group(groupKey);
            user(userId).groups.add(groupKey);
            save();
        }

        synchronized void removeUserGroup(String userId, String groupName) throws IOException, CommandException {
            user(userId).groups.remove(requireName(groupName));
            save();
        }

        synchronized PermissionDecision resolve(PermissionSubject subject, String permission) {
            if (subject == null || subject.userId() == null || subject.userId().isBlank()) {
                return null;
            }
            String node = normalizePermission(permission);
            if (node.isBlank()) {
                return new PermissionDecision(true, "empty-permission");
            }
            PermissionUser user = store.users.get(subject.userId().trim());
            if (user != null) {
                PermissionDecision decision = lookup(user.permissions, node, "user:" + subject.userId().trim());
                if (decision != null) {
                    return decision;
                }
            }
            for (PermissionGroup group : resolvedGroups(user)) {
                PermissionDecision decision = lookup(group.permissions, node, "group:" + group.name);
                if (decision != null) {
                    return decision;
                }
            }
            return null;
        }

        synchronized String formatGroups() {
            if (store.groups.isEmpty()) {
                return "当前没有权限组。";
            }
            StringBuilder builder = new StringBuilder("权限组列表：");
            store.groups.values().stream()
                    .sorted(Comparator.comparingInt(PermissionGroup::getWeight).reversed()
                            .thenComparing(PermissionGroup::getName))
                    .forEach(group -> builder.append('\n')
                            .append(group.name)
                            .append(" weight=")
                            .append(group.weight)
                            .append(" parents=")
                            .append(group.parents.isEmpty() ? "-" : String.join(",", group.parents)));
            return builder.toString();
        }

        synchronized String formatGroup(String name) throws CommandException {
            PermissionGroup group = group(name);
            return """
                    权限组：%s
                    权重：%d
                    继承：%s
                    权限：%s
                    """.formatted(
                    group.name,
                    group.weight,
                    group.parents.isEmpty() ? "-" : String.join(", ", group.parents),
                    formatPermissions(group.permissions)
            ).strip();
        }

        synchronized String formatUser(String userId) {
            String key = userId == null ? "" : userId.trim();
            PermissionUser user = store.users.getOrDefault(key, new PermissionUser());
            return """
                    用户：%s
                    权限组：%s
                    权限：%s
                    """.formatted(
                    key,
                    user.groups.isEmpty() ? "-" : String.join(", ", user.groups),
                    formatPermissions(user.permissions)
            ).strip();
        }

        private void ensureDefaults() {
            if (store.defaultGroup == null || store.defaultGroup.isBlank()) {
                store.defaultGroup = settings.getDefaultGroup();
            } else {
                store.defaultGroup = normalizeName(store.defaultGroup);
            }
            store.groups = normalizeGroups(store.groups);
            store.users = normalizeUsers(store.users);

            store.groups.computeIfAbsent(store.defaultGroup, key -> {
                PermissionGroup group = new PermissionGroup();
                group.name = key;
                group.weight = 0;
                return group;
            });

            if (settings.isCreateAdminGroup()) {
                store.groups.computeIfAbsent("admin", key -> {
                    PermissionGroup group = new PermissionGroup();
                    group.name = key;
                    group.weight = 100;
                    group.permissions.put("*", true);
                    group.permissions.put("luckperms.admin", true);
                    return group;
                });
            }
        }

        private Map<String, PermissionGroup> normalizeGroups(Map<String, PermissionGroup> groups) {
            Map<String, PermissionGroup> normalized = new LinkedHashMap<>();
            if (groups == null) {
                return normalized;
            }
            for (Map.Entry<String, PermissionGroup> entry : groups.entrySet()) {
                String key = normalizeName(entry.getKey());
                if (key.isBlank()) {
                    continue;
                }
                PermissionGroup group = entry.getValue() == null ? new PermissionGroup() : entry.getValue();
                group.name = key;
                group.parents = normalizeNames(group.parents);
                group.permissions = normalizePermissions(group.permissions);
                normalized.put(key, group);
            }
            return normalized;
        }

        private Map<String, PermissionUser> normalizeUsers(Map<String, PermissionUser> users) {
            Map<String, PermissionUser> normalized = new LinkedHashMap<>();
            if (users == null) {
                return normalized;
            }
            for (Map.Entry<String, PermissionUser> entry : users.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                if (key.isBlank()) {
                    continue;
                }
                PermissionUser user = entry.getValue() == null ? new PermissionUser() : entry.getValue();
                user.groups = normalizeNames(user.groups);
                user.permissions = normalizePermissions(user.permissions);
                normalized.put(key, user);
            }
            return normalized;
        }

        private LinkedHashSet<String> normalizeNames(Set<String> names) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (names == null) {
                return normalized;
            }
            for (String name : names) {
                String key = normalizeName(name);
                if (!key.isBlank()) {
                    normalized.add(key);
                }
            }
            return normalized;
        }

        private LinkedHashMap<String, Boolean> normalizePermissions(Map<String, Boolean> permissions) {
            LinkedHashMap<String, Boolean> normalized = new LinkedHashMap<>();
            if (permissions == null) {
                return normalized;
            }
            for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                String key = normalizePermission(entry.getKey());
                if (!key.isBlank()) {
                    normalized.put(key, Boolean.TRUE.equals(entry.getValue()));
                }
            }
            return normalized;
        }

        private PermissionGroup group(String name) throws CommandException {
            String key = requireName(name);
            PermissionGroup group = store.groups.get(key);
            if (group == null) {
                throw new CommandException("权限组不存在：" + key);
            }
            return group;
        }

        private PermissionUser user(String userId) {
            String key = userId == null ? "" : userId.trim();
            return store.users.computeIfAbsent(key, ignored -> new PermissionUser());
        }

        private String requireName(String value) throws CommandException {
            String key = normalizeName(value);
            if (key.isBlank()) {
                throw new CommandException("组名不能为空。");
            }
            return key;
        }

        private String requirePermission(String value) throws CommandException {
            String key = normalizePermission(value);
            if (key.isBlank()) {
                throw new CommandException("权限节点不能为空。");
            }
            return key;
        }

        private List<PermissionGroup> resolvedGroups(PermissionUser user) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            names.add(store.defaultGroup);
            if (user != null) {
                names.addAll(user.groups);
            }
            LinkedHashSet<String> resolved = new LinkedHashSet<>();
            for (String name : names) {
                collectGroup(name, resolved, new LinkedHashSet<>());
            }
            return resolved.stream()
                    .map(store.groups::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(PermissionGroup::getWeight).reversed()
                            .thenComparing(PermissionGroup::getName))
                    .toList();
        }

        private void collectGroup(String name, Set<String> resolved, Set<String> visiting) {
            String key = normalizeName(name);
            PermissionGroup group = store.groups.get(key);
            if (group == null || visiting.contains(key)) {
                return;
            }
            resolved.add(key);
            visiting.add(key);
            for (String parent : group.parents) {
                collectGroup(parent, resolved, visiting);
            }
            visiting.remove(key);
        }

        private PermissionDecision lookup(Map<String, Boolean> permissions, String permission, String source) {
            if (permissions == null || permissions.isEmpty()) {
                return null;
            }
            Boolean exact = permissions.get(permission);
            if (exact != null) {
                return new PermissionDecision(exact, source + ":" + permission);
            }
            String[] parts = permission.split("\\.");
            for (int i = parts.length - 1; i >= 1; i--) {
                String wildcard = String.join(".", List.of(parts).subList(0, i)) + ".*";
                Boolean value = permissions.get(wildcard);
                if (value != null) {
                    return new PermissionDecision(value, source + ":" + wildcard);
                }
            }
            Boolean global = permissions.get("*");
            return global == null ? null : new PermissionDecision(global, source + ":*");
        }

        private String formatPermissions(Map<String, Boolean> permissions) {
            if (permissions.isEmpty()) {
                return "-";
            }
            List<String> entries = new ArrayList<>();
            permissions.forEach((node, value) -> entries.add(node + "=" + value));
            return String.join(", ", entries);
        }
    }

    public static class PermissionStore {
        private String defaultGroup = "default";
        private Map<String, PermissionGroup> groups = new LinkedHashMap<>();
        private Map<String, PermissionUser> users = new LinkedHashMap<>();

        public String getDefaultGroup() {
            return defaultGroup;
        }

        public void setDefaultGroup(String defaultGroup) {
            this.defaultGroup = defaultGroup;
        }

        public Map<String, PermissionGroup> getGroups() {
            return groups;
        }

        public void setGroups(Map<String, PermissionGroup> groups) {
            this.groups = groups == null ? new LinkedHashMap<>() : groups;
        }

        public Map<String, PermissionUser> getUsers() {
            return users;
        }

        public void setUsers(Map<String, PermissionUser> users) {
            this.users = users == null ? new LinkedHashMap<>() : users;
        }
    }

    public static class PermissionGroup {
        private String name = "";
        private int weight;
        private LinkedHashSet<String> parents = new LinkedHashSet<>();
        private LinkedHashMap<String, Boolean> permissions = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public LinkedHashSet<String> getParents() {
            return parents;
        }

        public void setParents(LinkedHashSet<String> parents) {
            this.parents = parents == null ? new LinkedHashSet<>() : parents;
        }

        public LinkedHashMap<String, Boolean> getPermissions() {
            return permissions;
        }

        public void setPermissions(LinkedHashMap<String, Boolean> permissions) {
            this.permissions = permissions == null ? new LinkedHashMap<>() : permissions;
        }
    }

    public static class PermissionUser {
        private LinkedHashSet<String> groups = new LinkedHashSet<>();
        private LinkedHashMap<String, Boolean> permissions = new LinkedHashMap<>();

        public LinkedHashSet<String> getGroups() {
            return groups;
        }

        public void setGroups(LinkedHashSet<String> groups) {
            this.groups = groups == null ? new LinkedHashSet<>() : groups;
        }

        public LinkedHashMap<String, Boolean> getPermissions() {
            return permissions;
        }

        public void setPermissions(LinkedHashMap<String, Boolean> permissions) {
            this.permissions = permissions == null ? new LinkedHashMap<>() : permissions;
        }
    }
}
