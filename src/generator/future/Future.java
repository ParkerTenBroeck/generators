package generator.future;

public interface Future<R> {

    @SuppressWarnings("unchecked")
    default R poll(Waker waker){
        return (R) Pending.INSTANCE;
    }

    default R await(){
        throw new RuntimeException();
    }

    static <R> Future<R> ret(R r){
        throw new RuntimeException();
    }

    final class Pending{
        private static final Pending INSTANCE = new Pending();
        private Pending(){}
    }
}
