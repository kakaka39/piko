package app.morphe.extension.newx.settings;
import java.util.function.Supplier;
public final class NewXLogger {
    public static void printException(Supplier<String> message, Exception error) {
        throw new AssertionError(message.get(), error);
    }
}
