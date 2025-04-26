package generator.future;

public interface Future<R> {

    default Object poll(Waker waker){
        return Pending.INSTANCE;
    }

    default R await(){
        throw new RuntimeException("NO!");
    }

    static <R> Future<R> ret(R r){
        throw new RuntimeException("NO!");
    }

    static void yield() {
        throw new RuntimeException("NO!");
    }

    final class Pending{
        public static final Pending INSTANCE = new Pending();
        private Pending(){}
    }
}
