package remila.sex.cirrus.verification;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.function.BiConsumer;

/** GPU-free native command construction shared by version-specific draw checks. */
final class NativeTerrainDraw {
    private static final BiConsumer<Object, Object> UPLOADER = (a, b) -> {};

    private NativeTerrainDraw() {}

    static Object create(Class<?> type, int count, int first) throws ReflectiveOperationException {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = new Object[components.length];
        for (int i = 0; i < values.length; i++) {
            String name = components[i].getName();
            if (types[i] == int.class) values[i] = name.equals("indexCount") ? count : name.equals("firstIndex") ? first : 17 + i;
            else if (types[i].isEnum()) values[i] = types[i].getEnumConstants()[0];
            else if (types[i] == BiConsumer.class) values[i] = UPLOADER;
        }
        Constructor<?> constructor = type.getConstructor(types);
        return constructor.newInstance(values);
    }

    static int integer(Object draw, String name) throws ReflectiveOperationException {
        return (int) draw.getClass().getMethod(name).invoke(draw);
    }

    static void verifyPreserved(Object original, Object result) throws ReflectiveOperationException {
        for (RecordComponent component : original.getClass().getRecordComponents()) {
            if (component.getName().equals("firstIndex") || component.getName().equals("indexCount")) continue;
            Object before = component.getAccessor().invoke(original), after = component.getAccessor().invoke(result);
            if (!java.util.Objects.equals(before, after)) throw new AssertionError("Draw lost " + component.getName());
        }
    }
}
