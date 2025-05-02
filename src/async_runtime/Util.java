package async_runtime;

import future.Future;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public static <T, E extends Throwable> Future<T[], E> all(Future<T, E>... futures){
        return waker -> {
            return Future.Pending.INSTANCE;
        };
    }

    public static <T, E extends Throwable> Future<List<T>, E> all(List<Future<T, E>> futures){
        var resolved = new ArrayList<T>(Collections.nCopies(futures.size(), null));
        return waker -> {
            for(int i = 0; i < resolved.size(); i ++){

            }
            return Future.Pending.INSTANCE;
        };
    }
}
