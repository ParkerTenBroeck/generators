package generator.future;

public interface Future<R, E extends Throwable> {

    default Object poll(Waker waker) throws E{
        return Pending.INSTANCE;
    }

    default R await() throws E{
        throw new RuntimeException("NO!");
    }

    default void cancel(){}

    static <R, E extends Throwable> Future<R, E> ret(R r){
        throw new RuntimeException("NO!");
    }

    static <E extends Throwable> Future<Void, E> ret(){
        throw new RuntimeException();
    }

    static void yield() {
        throw new RuntimeException("NO!");
    }

    final class Pending{
        public static final Pending INSTANCE = new Pending();
        private Pending(){}
    }
}
