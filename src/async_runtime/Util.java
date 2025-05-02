package async_runtime;

import future.Future;

import java.util.*;
import java.util.function.Function;

public class Util {

    @SafeVarargs
    public static <T, E extends Throwable> Future<T, E> first(Future<T, E>... futures){
        return waker -> {
            int i;
            boolean resolved = false;
            Object result = Future.Pending.INSTANCE;
            for(i = 0; i < futures.length; i ++){
                result = futures[i].poll(waker);
                resolved = result!=Future.Pending.INSTANCE;
                if(resolved)break;
            }
            if(resolved){
                for(int j = 0; j < futures.length; j ++)
                    if(j!=i)
                        futures[j].cancel();
            }
            return result;
        };
    }

    public static <T, E extends Throwable> Future<T, E> first(List<Future<T, E>> futures){
        return waker -> {
            int i = 0;
            boolean resolved = false;
            Object result = Future.Pending.INSTANCE;
            for(var el : futures){
                result = el.poll(waker);
                resolved = result!=Future.Pending.INSTANCE;
                if(resolved)break;
                i++;
            }
            if(resolved){
                int j = 0;
                for(var el : futures){
                    if(i==j)
                        el.cancel();
                    j++;
                }
            }
            return result;
        };
    }

    public interface Func<R, T, E extends Throwable>{
        R call(T p) throws E;
    }
    public interface FuncV<T, E extends Throwable>{
        void call(T p) throws E;
    }
    public record Selectee<R, T, E extends Throwable>(Future<T, ? extends E> future, Func<R, T, ? extends E> acceptor){ }
    public static <R, T, E extends Throwable> Selectee<R, T, E> selectee(Future<T, ? extends E> future, Func<R, T, ? extends E> acceptor){
        return new Selectee<>(future, acceptor);
    }
    public static <T, E extends Throwable> Selectee<Void, T, E> selectee(Future<T, ? extends E> future, FuncV<T, ? extends E> acceptor){
        return new Selectee<>(future, v -> {
            acceptor.call(v);
            return null;
        });
    }
    @SuppressWarnings("unchecked")
    public static <R, E extends Throwable> Future<R, ? extends E> select(Selectee<? extends R, ?, ? extends E>... selectees){
        return waker -> {
            int i;
            Object result = Future.Pending.INSTANCE;
            for(i = 0; i < selectees.length; i ++){
                result = selectees[i].future.poll(waker);
                if(result!=Future.Pending.INSTANCE)break;
            }

            if(result!=Future.Pending.INSTANCE) {
                for (int j = 0; j < selectees.length; j++)
                    if(j==i)
                        selectees[i].future.cancel();

                return ((Func<R, Object, E>)selectees[i].acceptor).call(result);
            }
            return Future.Pending.INSTANCE;
        };
    }

    public static <T, E extends Throwable> Future<T[], E> all(Future<T, E>... futures){
        var resolved = new Object[futures.length];
        Arrays.fill(resolved,  Future.Pending.INSTANCE);
        return waker -> {
            boolean done = true;
            for(int i = 0; i < resolved.length; i ++){
                if(resolved[i]==Future.Pending.INSTANCE){
                    var result = futures[i].poll(waker);
                    resolved[i] = result;
                    done &= result != Future.Pending.INSTANCE;
                }
            }
            if(done)return resolved;
            return Future.Pending.INSTANCE;
        };
    }

    public static <T, E extends Throwable> Future<List<T>, E> all(List<Future<T, E>> futures){
        var resolved = new ArrayList<Object>(Collections.nCopies(futures.size(), Future.Pending.INSTANCE));
        return waker -> {
            boolean done = true;
            for(int i = 0; i < resolved.size(); i ++){
                if(resolved.get(i)==Future.Pending.INSTANCE){
                    var result = futures.get(i).poll(waker);
                    resolved.set(i, result);
                    done &= result != Future.Pending.INSTANCE;
                }
            }
            if(done)return resolved;
            return Future.Pending.INSTANCE;
        };
    }
}
