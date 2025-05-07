# Generators/Futures for Java 24+

Futures/Generators implemented as state machines integrated into standard java.

- Futures/Generators are lazy and only make progress when called upon
- Integrated as much as possible into java to work with the type system as much as possible
- Little overhead and runtime agnostic

This is not production ready and more of a POC.

```java
public static Future<Void, IOException> echo(@Cancellation("close") Socket socket) throws IOException {
    try(socket){
        var buffer = ByteBuffer.allocate(4096*2);
        while(true){
            bytes_received = socket.read(buffer).await() + bytes_received;
            buffer.flip();
            bytes_sent = socket.write_all(buffer).await() + bytes_sent;
            buffer.clear().limit(buffer.capacity());
        }
    }
}
```

```java
public static Gen<Long, Void> primes() {
    long number = 1;
    Gen.yield(2L);
    outer: while(true){
        number += 2;
        for(long i=2; i <= Math.sqrt(number); i ++){
            if(number%i==0)continue outer;
        }
        Gen.yield(number);
    }
}
```

## Use this library

```kotlin
// settings.gradle.kts
sourceControl {
    gitRepository(URI.create("https://github.com/ParkerTenBroeck/generators.git")) {
        producesModule("com.parkertenbroeck.generators:lib")
    }
}

// build.gradle.kts
implementation("com.parkertenbroeck.generators:lib:0.1.0")
```

This library requires the application be ran with a custom class loader, there is a utility provided to make this easier.
```java
public static void main(String[] args) {
    // loads the current class with a custom class loader and calls *this* method with the provided arguments.
    // *this* method - the one used to call `runWithStateMachines`
    RT.runWithStateMachines(StateMachineClassLoader.Config.builtin(), (Object) args); 

    // past this point generators will be created on methods which match the criteria
}
```

## How does it know what functions to transform?

Detection is done by 
1) return type (`Gen`/`Future`) 
2) if the function body contains at least one "special" function call

| Class    | Method  | Static |
| -------- | ------- | ------ |
| Future   | await   | false  |
| Future   | ret     | true   |
| Future   | yield   | true   |
| Waker    | waker   | true   |
| Gen      | yield   | true   | 
| Gen      | ret     | true   |

The second step is to allow regular functions to still exist without being turned into a state machine. Useful for library / manual implementations of the interfaces
```java
public static Future<Void, RuntimeException> regular_function() {
  return waker -> {
    waker.wake();
    return Future.Pending.INSTANCE;
  };
}
```

### Why not annotations?
Unfortunately annotations cannot be put on lambdas. 


## Oddities

```java
public static Future<Void, IOException> echo(🔷@Cancellation("close") Socket socket) 🔶throws IOException {
    try(socket){
        var buffer = ByteBuffer.allocate(4096*2);
        while(true){
            bytes_received = socket.read(buffer).await() + bytes_received;
            buffer.flip();
            bytes_sent = socket.write_all(buffer).await() + bytes_sent;
            buffer.clear().limit(buffer.capacity());
        }
    }
}
```
- 🔷 Local variables inside futures can be annotated with `@Cancellation` with an optional name (defaults to "cancel"). If the future is cancelled with the variable in scope the method with the name specified will be called on the variable.
- 🔶 Exceptions declared by generated future functions will never be thrown at the call site. 

## Things to watch out for

### Await function works specifically for the Future type

```java
class MyFuture implements Future<String, RuntimeException>{
    public Object poll(Waker waker){ /* code */ }
}

void Future<Void, RuntimeException> wrong() {
    new MyFuture().await();//⚡
    return Future.ret();
}
```
Internally the await call will become a `invokevirtual` call to `await` on `MyFuture`. Which will not currently be recognized as a "special" method to transform.

To avoid this use static methods to return a future and make manually implemented futures have private/protected constructors
```java
class MyFuture implements Future<String, RuntimeException>{
    private MyFuture(){}
    public static Future<String, RuntimeException> make(){
        return new MyFuture();
    }
    public Object poll(Waker waker){ /* code */ }
}

void Future<Void, RuntimeException> wrong() {
    MyFuture.make().await();
    return Future.ret();
}
```


### Future exceptions are not type checked
```java
public static Future<Void, RuntimeException> wrong() throws Exception {
  Waker.waker().wake();
  Future.yield();
  throw new Exception();//⚡
}
```

### Generator yields are not type checked 
```java
public static Gen<Long, Void> two(){
    Gen.yield(2);//⚡ 2 is an integer 
    return Gen.ret();
}
```

### order of operations
when working with shared state + futures it is important to watch out for the order which certain operations happen.
Consider the following, even if the runtime is singled threaded the order which the code is evaluated can cause unintended behavior.
```java
static int value = 2;
public static Future<Void, RuntimeException> add() {
  value += some_async_integer().await();//❓
}
```
What the compiler actually generates looks something like 
```java
int var1 = value;
int var2 = some_async_integer().await();
int var3 = var1 + var2;
value = var3;
```
if something else modifies `value` while this future is waiting to be woken it will overwrite any changes once resumed.

#### Solution, change order or assign to temporary
```java
static int value = 2;
public static Future<Void, RuntimeException> add_tmp() {
  var tmp = some_async_integer().await(); 
  value += tmp;
}
public static Future<Void, RuntimeException> add_reorder() {
  value = some_async_integer().await() + value;
}
```

### synchronized

when a generator/future method is declared as `synchronized` the `next`/`poll`/`cancel` functions will all be synchronized over the instance or class the method was declared in.

it is important to note that the monitor is **NOT** held across suspend points where the function returns.

Suspending in synchronized blocks is not supported and will throw `IllegalMonitorStateException` at runtime
```java
static Future<Void, RuntimeException> unsync(Object value) {
  synchronized(value){
    Delay.delat(500).await();//⚡
  }
  return Future.ret();
}
```

## How this works

Say we have some async function like this.
```java
public static Future<Void, IOException> example(@Cancellation("close") Socket socket, String message) throws IOException {
    try(socket){
        socket.write_all(ByteBuffer.wrap(message.getBytes())).await();
    }
    return Future.ret();
}
```
The loader will see the methods which it needs to transform, then modify the bytecode, transforming the function into a class where state across suspend points is saved/restored (local variables & the current state of the stack). 

This is a (modified) decompiled version of the generated method. 
```java
public static Future<Void, IOException> example(Socket socket, String message) {
    return new Future<>() {
        private Socket m_socket;
        private String m_message;
        private int state;
        private Future<Integer, IOException> awaiting;

        {
            this.m_socket = socket;
            this.m_message = message;
        }

        public Object poll(Waker waker) throws IOException {
            try {
                Throwable error;
                Object result;
                outer: {
                    Future<Integer, IOException> future;
                    switch (this.state) {
                        case 0:
                            try {
                                future = this.m_socket.write_all(ByteBuffer.wrap(this.m_message.getBytes()));
                                break;
                            } catch (Throwable e) {
                                error = e;
                                break outer;
                            }
                        case 1:

                            try {
                                future = this.awaiting;
                                this.awaiting = null;
                                break;
                            } catch (Throwable e) {
                                error = e;
                                break outer;
                            }
                        default:
                            throw new IllegalStateException();
                    }

                    awaiting: {
                        try {
                            if ((result = future.poll(waker)) instanceof Future.Pending) {
                                this.state = 1;
                                this.awaiting = future;
                                break awaiting;
                            }
                        } catch (Throwable e) {
                            error = e;
                            break outer;
                        }
                        if (m_socket != null)
                            m_socket.close();
                        this.state = -1;
                        return null;
                    }

                    return result;
                }

                if (error != null) {
                    try {
                        m_socket.close();
                    } catch (Throwable e) {
                        error.addSuppressed(e);
                    }
                }

                throw error;
            } catch (Throwable var14) {
                this.state = -1;
                throw (IOException)var14;
            }
        }

        public void cancel() throws IOException{
            if (this.state == -1) return;
            Throwable t = null;
            this.state = -1;
            if (this.awaiting != null) {
                try {
                    this.awaiting.cancel();
                    this.awaiting = null;
                } catch (Throwable e) {
                    t = e;
                }
            }

            try {
                this.m_socket.close();
            } catch (Throwable e) {
                if (t != null) t.addSuppressed(e); else t = e;
            }

            if (t != null) 
                throw (IOException) t;
        }
    };
}
```

## Potential additions

- Type checking during transformations
- Better debugger support (Currently line stepping is generally supported but most breakpoint sets are not working)
- Annotations (possibly integration during build time)
- More library features (async)
