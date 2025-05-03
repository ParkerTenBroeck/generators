package future;

public interface Future<R, E extends Throwable> {

    Object poll(Waker waker) throws E;

    default R await() throws E{
        throw new RuntimeException("NO!");
    }

    default void cancel(){}

    default Future<R, E> non_cancelable(){
        return this::poll;
    }

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
