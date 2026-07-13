package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.LiteralArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.PrimitiveType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IFieldInfoRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 常量存储器，用于管理类中的常量字段信息
 * 支持全局常量和按类隔离的常量存储，可用于常量替换优化
 */
public class ConstStorage {

    /** 是否启用常量替换优化 */
    private final boolean replaceEnabled;
    /** 全局常量值存储 (存放公共常量) */
    private final ValueStorage globalValues = new ValueStorage();
    /** 按类隔离的常量值存储映射 (存放各类的非公共常量) */
    private final Map<ClassNode, ValueStorage> classes = new HashMap<>();
    /** 资源 ID 到资源名称的映射 (例如 R.string.app_name) */
    private Map<Integer, String> resourcesNames = new HashMap<>();

    /**
     * 构造常量存储器
     *
     * @param args Jadx 配置参数，决定是否启用常量替换
     */
    public ConstStorage(JadxArgs args) {
        this.replaceEnabled = args.isReplaceConsts();
    }

    /**
     * 添加常量字段根据可见性决定存储到全局或类级别
     *
     * @param fld     字段节点
     * @param value   常量值
     * @param isPublic 是否为公共常量
     */
    public void addConstField(FieldNode fld, Object value, boolean isPublic) {
        if (isPublic) {
            addGlobalConstField(fld, value);
        } else {
            getClsValues(fld.getParentClass()).put(value, fld);
        }
    }

    /**
     * 添加全局常量字段
     *
     * @param fld   字段引用
     * @param value 常量值
     */
    public void addGlobalConstField(IFieldInfoRef fld, Object value) {
        globalValues.put(value, fld);
    }

    /**
     * 移除指定类的所有常量记录，包括类级别和全局中属于该类的条目
     *
     * @param cls 要移除的类节点
     */
    public void removeForClass(ClassNode cls) {
        classes.remove(cls);
        globalValues.removeForCls(cls);
    }

    /** 获取指定类的值存储，不存在时自动创建 */
    private ValueStorage getClsValues(ClassNode cls) {
        return classes.computeIfAbsent(cls, c -> new ValueStorage());
    }

    /**
     * 查找与给定值匹配的常量字段
     * 按以下优先级查找：资源字段 -> 当前类及父类 -> 全局常量
     * 如果值在全局中存在重复，则不进行替换以避免歧义
     *
     * @param cls          当前类节点
     * @param value        要查找的常量值
     * @param searchGlobal 是否搜索全局常量
     * @return 匹配的字段引用，未找到返回 null
     */
    public @Nullable IFieldInfoRef getConstField(ClassNode cls, Object value, boolean searchGlobal) {
        if (!replaceEnabled) {
            return null;
        }
        RootNode root = cls.root();
        if (value instanceof Integer) {
            FieldNode rField = getResourceField((Integer) value, root);
            if (rField != null) {
                return rField;
            }
        }
        boolean foundInGlobal = globalValues.contains(value);
        if (foundInGlobal && !searchGlobal) {
            return null;
        }
        ClassNode current = cls;
        while (current != null) {
            ValueStorage classValues = classes.get(current);
            if (classValues != null) {
                IFieldInfoRef field = classValues.get(value);
                if (field != null) {
                    if (foundInGlobal) {
                        return null;
                    }
                    return field;
                }
            }
            ClassInfo parentClass = current.getClassInfo().getParentClass();
            if (parentClass == null) {
                break;
            }
            current = root.resolveClass(parentClass);
        }
        if (searchGlobal) {
            return globalValues.get(value);
        }
        return null;
    }

    /**
     * 根据资源 ID 查找对应的资源字段
     * 从资源名称映射中解析类型名和字段名，然后在应用资源类的内部类中查找
     *
     * @param value 资源 ID
     * @param root  根节点
     * @return 匹配的资源字段，未找到返回 null
     */
    @Nullable
    private FieldNode getResourceField(Integer value, RootNode root) {
        String str = resourcesNames.get(value);
        if (str == null) {
            return null;
        }
        ClassNode appResClass = root.getAppResClass();
        if (appResClass == null) {
            return null;
        }
        String[] parts = str.split("/", 2);
        if (parts.length != 2) {
            return null;
        }
        String typeName = parts[0];
        String fieldName = parts[1];
        for (ClassNode innerClass : appResClass.getInnerClasses()) {
            if (innerClass.getClassInfo().getShortName().equals(typeName)) {
                return innerClass.searchFieldByName(fieldName);
            }
        }
        appResClass.addWarn("Not found resource field with id: " + value + ", name: " + str.replace('/', '.'));
        return null;
    }

    /**
     * 根据字面量参数查找对应的常量字段
     * 根据参数的原始类型 (boolean char byte short int long float double)
     * 将字面量转换为对应类型的值，然后委托 {@link #getConstField} 查找
     * 对于较小的绝对值，不搜索全局常量以减少误匹配
     *
     * @param cls 当前类节点
     * @param arg 字面量参数
     * @return 匹配的字段引用，未找到返回 null
     */
    public @Nullable IFieldInfoRef getConstFieldByLiteralArg(ClassNode cls, LiteralArg arg) {
        if (!replaceEnabled) {
            return null;
        }
        PrimitiveType type = arg.getType().getPrimitiveType();
        if (type == null) {
            return null;
        }
        long literal = arg.getLiteral();
        switch (type) {
            case BOOLEAN:
                return getConstField(cls, literal == 1, false);
            case CHAR:
                return getConstField(cls, (char) literal, Math.abs(literal) > 10);
            case BYTE:
                return getConstField(cls, (byte) literal, Math.abs(literal) > 10);
            case SHORT:
                return getConstField(cls, (short) literal, Math.abs(literal) > 100);
            case INT:
                return getConstField(cls, (int) literal, Math.abs(literal) > 100);
            case LONG:
                return getConstField(cls, literal, Math.abs(literal) > 1000);
            case FLOAT:
                float f = Float.intBitsToFloat((int) literal);
                return getConstField(cls, f, Float.compare(f, 0) == 0);
            case DOUBLE:
                double d = Double.longBitsToDouble(literal);
                return getConstField(cls, d, Double.compare(d, 0) == 0);

            default:
                return null;
        }
    }

    /**
     * 获取资源 ID 到资源名称的映射
     *
     * @return 资源 ID 到资源名称的映射
     */
    public Map<Integer, String> getResourcesNames() {
        return resourcesNames;
    }

    /**
     * 设置资源 ID 到资源名称的映射
     *
     * @param resourcesNames 资源 ID 到资源名称的映射
     */
    public void setResourcesNames(Map<Integer, String> resourcesNames) {
        this.resourcesNames = resourcesNames;
    }

    /**
     * 获取全局常量字段映射 (值到字段引用)
     *
     * @return 全局常量字段映射
     */
    public Map<Object, IFieldInfoRef> getGlobalConstFields() {
        return globalValues.getValues();
    }

    /**
     * 判断是否启用了常量替换优化
     *
     * @return 启用返回 true，否则返回 false
     */
    public boolean isReplaceEnabled() {
        return replaceEnabled;
    }

    /**
     * 值存储，维护值到字段引用的映射关系
     * 当同一个值对应多个字段时，标记为重复值并移除映射，以避免歧义替换
     */
    private static final class ValueStorage {
        private final Map<Object, IFieldInfoRef> values = new ConcurrentHashMap<>();
        private final Set<Object> duplicates = new HashSet<>();

        /** 获取值到字段引用的完整映射 */
        Map<Object, IFieldInfoRef> getValues() {
            return values;
        }

        /** 根据值查找对应的字段引用 */
        IFieldInfoRef get(Object key) {
            return values.get(key);
        }

        /**
         * 存储值与字段的映射关系
         * 如果该值已存在重复记录，或插入时发现冲突，则标记为重复值并移除映射
         *
         * @return 如果该值是重复值 (即多个字段拥有相同的常量值)则返回 true
         */
        boolean put(Object value, IFieldInfoRef fld) {
            if (duplicates.contains(value)) {
                values.remove(value);
                return true;
            }
            IFieldInfoRef prev = values.put(value, fld);
            if (prev != null) {
                values.remove(value);
                duplicates.add(value);
                return true;
            }
            return false;
        }

        /** 检查该值是否已存在 (包括重复值) */
        public boolean contains(Object value) {
            return duplicates.contains(value) || values.containsKey(value);
        }

        /** 移除指定类对应的所有字段映射 */
        void removeForCls(ClassNode cls) {
            values.entrySet().removeIf(entry -> {
                IFieldInfoRef field = entry.getValue();
                if (field instanceof FieldNode) {
                    return ((FieldNode) field).getParentClass().equals(cls);
                }
                return false;
            });
        }
    }
}
