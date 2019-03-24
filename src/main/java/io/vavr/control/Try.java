/* ____  ______________  ________________________  __________
 * \   \/   /      \   \/   /   __/   /      \   \/   /      \
 *  \______/___/\___\______/___/_____/___/\___\______/___/\___\
 *
 * Copyright 2019 Vavr, http://vavr.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.vavr.control;

import io.vavr.collection.Iterator;

import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * The {@code Try} control gives us the ability to write safe code without focusing on try-catch blocks in the presence
 * of exceptions.
 * <p>
 * A real-world use-case is to defer error handling and recovery to outer applications layers. With {@code Try}, we
 * achieve this by capturing the error state of a computation and passing it around.
 * <p>
 * {@code Try} has one of two states, {@code Success} and {@code Failure}. A {@code Success} wraps the value of a given
 * computation, a {@code Failure} wraps an exception that occurred during the computation.
 * <p>
 * The following exceptions are considered to be fatal/non-recoverable and will be re-thrown:
 *
 * <ul>
 * <li>{@linkplain LinkageError}</li>
 * <li>{@linkplain ThreadDeath}</li>
 * <li>{@linkplain VirtualMachineError} (i.e. {@linkplain OutOfMemoryError} or {@linkplain StackOverflowError})</li>
 * </ul>
 *
 * <h2>Creation</h2>
 *
 * Try is intended to be used as value which contains the result of a computation. For that purpose,
 * {@link #of(CheckedSupplier)} is called. See also {@link #success(Object)} and {@link #failure(Throwable)}.
 * <p>
 * However, some use {@code Try} as syntactic sugar for try-catch blocks that only perform side-effects. For that purpose,
 * {@link #run(CheckedRunnable)} is called. This variant does not contain a value but is still able to observe, handle
 * and recover an error state.
 *
 * <h2>Capturing exceptions</h2>
 *
 * Opposed to other types, higher-order functions that <em>transform</em> this type take checked functions, or more
 * precisely, lambdas or method references that may throw
 * <a href="https://www.baeldung.com/java-lambda-exceptions">checked exceptions</a>.
 * <p>
 * We intentionally do not provide alternate methods that take unchecked functions (like {@code map} vs {@code mapTry}).
 * Instead we make it explicit on the API layer that exceptions are properly handled when transforming values.
 * An exception will not escape the context of a {@code Try} in these cases.
 * <p>
 * Another reason for not providing unchecked variants is that Vavr's higher-order functions always take the most
 * general argument type. Checked functions that may throw {@code Throwable} are more general than unchecked functions
 * because unchecked exceptions are restricted to throw runtime exceptions.
 * <p>
 * Higher-order functions that return a concrete value, like {@link #getOrElseGet(Supplier)} and
 * {@link #fold(Function, Function)}, will not handle exceptions when calling function arguments. The parameter
 * types make this clear.
 *
 * <h2>Transforming a Try</h2>
 *
 * Transformations that are focused on a successful state are:
 *
 * <ul>
 * <li>{@link #map(CheckedFunction)}</li>
 * <li>{@link #flatMap(CheckedFunction)}</li>
 * <li>{@link #filter(CheckedPredicate)}</li>
 * </ul>
 *
 * Transformations that are focused on a failed state are:
 *
 * <ul>
 * <li>{@link #failed()} - transforms a failure into a success</li>
 * <li>{@link #mapFailure(CheckedFunction)} - transforms the cause of a failure</li>
 * <li>{@link #orElse(CheckedSupplier)} - performs another computation in the case of a failure</li>
 * <li>{@link #recover(Class, CheckedFunction)} - recovers a specific failure by providing an alternate value</li>
 * <li>{@link #recoverWith(Class, CheckedFunction)} - recovers a specific failure by performing an alternate computation</li>
 * </ul>
 *
 * More general transformations that take both states (success/failure) into account are:
 *
 * <ul>
 * <li>{@link #fold(Function, Function)}</li>
 * <li>{@link #transform(CheckedFunction, CheckedFunction)}</li>
 * </ul>
 *
 * <h2>Handling the state of a Try</h2>
 *
 * Opposed to Java (see {@link Optional#ifPresent(Consumer)}), we are able to chain one or more of the following actions:
 *
 * <ul>
 * <li>{@link #onFailure(Consumer)}</li>
 * <li>{@link #onSuccess(Consumer)}</li>
 * </ul>
 *
 * <h2>Getting the value of a Try</h2>
 *
 * At some point, we might need to operate on the unwrapped value of a Try. These are our options to reduce a successful
 * or failed state to a value:
 *
 * <ul>
 * <li>{@link #fold(Function, Function)} - <strong>safe</strong> alternative to get()</li>
 * <li>{@link #get()} - <strong>unsafe</strong>, throws in the case of a failure</li>
 * <li>{@link #getOrElse(Object)}</li>
 * <li>{@link #getOrElseGet(Supplier)}</li>
 * <li>{@link #getOrElseThrow(Function)}</li>
 * </ul>
 *
 * <h2>Try with resources</h2>
 *
 * It is also possible to use {@code Try} directly with {@link AutoCloseable} resources:
 *
 * <pre>{@code
 * final Try<T> calc = Try.of(() -> {
 *     try (final ac1 = someAutoCloseable1(); ...; final acn = someAutoCloseableN()) {
 *         return doSth(ac1, ..., acn);
 *     } finally {
 *         doSth();
 *     }
 * });
 * }</pre>
 *
 * @param <T> Value type of a successful computation
 * @author Daniel Dietrich
 */
public abstract class Try<T> implements io.vavr.Iterable<T>, Serializable {

    private static final long serialVersionUID = 1L;

    // sealed
    private Try() {}

    /**
     * Creates a Try of a {@link CheckedSupplier}.
     * <p>
     * If the computation leads to a {@code Failure(cause)} and the underlying {@code cause} is an
     * {@code InterruptedException}, this methods calls {@code Thread.currentThread().interrupt()}.
     *
     * @param supplier A supplier that may throw a checked exception
     * @param <T>      Component type
     * @return {@code Success(supplier.get())} if no exception occurs, otherwise {@code Failure(cause)} if a
     * non-fatal error occurs calling {@code supplier.get()}.
     * @throws Error if the cause of the {@code Failure} is fatal, i.e. non-recoverable
     */
    public static <T> Try<T> of(CheckedSupplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        try {
            return success(supplier.get());
        } catch (Throwable t) {
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return failure(t);
        }
    }

    /**
     * Runs a {@code CheckedRunnable} and captures any non-fatal exception in a {@code Try}.
     * <p>
     * Because running a unit of work is all about performing side-effects rather than returning a value,
     * a {@code Try<Void>} is created.
     *
     * @param runnable A checked runnable, i.e. a runnable that may throw a checked exception.
     * @return {@code Success(null)} if no exception occurs, otherwise {@code Failure(throwable)} if an exception occurs
     * calling {@code runnable.run()}.
     * @throws Error if the cause of the {@code Failure} is fatal, i.e. non-recoverable
     */
    public static Try<Void> run(CheckedRunnable runnable) {
        Objects.requireNonNull(runnable, "runnable is null");
        return of(() -> {
            runnable.run();
            return null; // null represents the absence of an value, i.e. Void
        });
    }

    /**
     * Creates a {@code Success} that contains the given {@code value}.
     *
     * @param value A value.
     * @param <T>   Type of the given {@code value}.
     * @return A new {@code Success}.
     */
    public static <T> Try<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * Creates a {@code Failure} that contains the given {@code exception}.
     *
     * @param exception An exception. Please note that a null value is allowed but discouraged.
     * @param <T>       Component type of the {@code Try}.
     * @return A new {@code Failure}.
     * @throws Error if the given {@code exception} is fatal, i.e. non-recoverable
     */
    public static <T> Try<T> failure(Throwable exception) {
        return new Failure<>(exception);
    }

    /**
     * Collects the underlying value (if present) using the provided {@code collector}.
     * <p>
     * Shortcut for {@code .stream().collect(collector)}.
     *
     * @param <A>       the mutable accumulation type of the reduction operation
     * @param <R>       the result type of the reduction operation
     * @param collector Collector performing reduction
     * @return the reduction result of type {@code R}
     * @throws NullPointerException if the given {@code collector} is null
     */
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return stream().collect(collector);
    }

    /**
     * Inverts this {@code Try}.
     *
     * @return {@code Success(throwable)} if this is a {@code Failure(throwable)},
     *         otherwise a {@code Failure(new UnsupportedOperationException("Success.failed()"))} if this is a
     *         {@code Success}.
     */
    public Try<Throwable> failed() {
        if (isFailure()) {
            return new Success<>(getCause());
        } else {
            return failure(new UnsupportedOperationException("Success.failed()"));
        }
    }

    /**
     * Returns {@code this} if this is a Failure or this is a Success and the value satisfies the predicate.
     * <p>
     * Returns a new Failure, if this is a Success and the value does not satisfy the Predicate or an exception
     * occurs testing the predicate. The returned Failure wraps a {@link NoSuchElementException} instance.
     *
     * @param predicate A checked predicate
     * @return a {@code Try} instance
     * @throws NullPointerException if {@code predicate} is null
     */
    public Try<T> filter(CheckedPredicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        if (isSuccess()) {
            try {
                final T value = get();
                if (!predicate.test(value)) {
                    return failure(new NoSuchElementException("Predicate does not hold for " + value));
                }
            } catch (Throwable t) {
                return failure(t);
            }
        }
        return this;
    }

    /**
     * FlatMaps the value of a Success or returns a Failure.
     *
     * @param mapper A mapper
     * @param <U>    The new component type
     * @return a {@code Try}
     * @throws NullPointerException if {@code mapper} is null
     */
    @SuppressWarnings("unchecked")
    public <U> Try<U> flatMap(CheckedFunction<? super T, ? extends Try<? extends U>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (isSuccess()) {
            try {
                return (Try<U>) mapper.apply(get());
            } catch (Throwable t) {
                return failure(t);
            }
        } else {
            return (Try<U>) this;
        }
    }

    /**
     * Folds either the {@code Failure} or the {@code Success} side of the Try value.
     *
     * @param ifFailure maps the cause if this is a {@code Failure}
     * @param ifSuccess maps the value if this is a {@code Success}
     * @param <U>       type of the folded value
     * @return A value of type U
     * @throws NullPointerException if one of the given {@code ifFailure} or {@code ifSuccess} is null
     */
    public <U> U fold(Function<? super Throwable, ? extends U> ifFailure, Function<? super T, ? extends U> ifSuccess) {
        Objects.requireNonNull(ifFailure, "ifFailure is null");
        Objects.requireNonNull(ifSuccess, "ifSuccess is null");
        return isSuccess() ? ifSuccess.apply(get()) : ifFailure.apply(getCause());
    }

    /**
     * Gets the result of this Try if this is a {@code Success} or throws if this is a {@code Failure}.
     * <p>
     * If this is a {@code Failure} the cause is thrown using the following strategy:, it will <em>rethrow</em> the original cause if it is an {@link Error} or a {@link RuntimeException}.
     *
     * <ul>
     *   <li>The original cause is <em>rethrown</em>, if it is an unchecked exception (namely an {@link Error} or a {@link RuntimeException}).</li>
     *   <li>If the cause is a checked exception, it is <em>wrapped</em> in an unchecked {@link NonFatalException} and thrown.</li>
     * </ul>
     *
     * <strong>Warning:</strong> Please note that this operation is considered unsafe.
     * Alternatives are {@link #fold(Function, Function)}, {@link #getOrElse(Object)}, {@link #getOrElseGet(Supplier)}
     * or {@link #getOrElseThrow(Function)}.
     * Other alternatives are {@link #onSuccess(Consumer)}, {@link #forEach(Consumer)} or iteration using a for-loop.
     *
     * @return The computation result if this is a {@code Success}
     * @throws Error if this is a {@code Failure} and the cause is an {@code Error}
     * @throws RuntimeException if this is a {@code Failure} and the cause is a {@code RuntimeException}.
     *         If the cause is null, a {@code NullPointerException is thrown}.
     * @throws NonFatalException if this is a {@code Failure} and the cause is a checked {@code Exception}
     */
    public abstract T get() throws Error, RuntimeException;

    /**
     * Gets the cause if this is a Failure or throws if this is a Success.
     * <p>
     * <strong>Warning:</strong> Please note that this operation is considered unsafe.
     * Alternatives are {@link #fold(Function, Function)} and {@link #onFailure(Consumer)}.
     *
     * @return The (possibly null) cause if this is a Failure
     * @throws UnsupportedOperationException if this is a Success
     */
    public abstract Throwable getCause() throws UnsupportedOperationException;

    /**
     * Returns the underlying value if present, otherwise {@code other}.
     *
     * @param other An alternative value.
     * @return A value of type {@code T}
     */
    public T getOrElse(T other) {
        return isSuccess() ? get() : other;
    }

    /**
     * Returns the underlying value if present, otherwise the result of {@code other.get()}.
     *
     * @param supplier A {@code Supplier} of an alternative value.
     * @return A value of type {@code T}
     * @throws NullPointerException if the given {@code other} is null
     */
    public T getOrElseGet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        return isSuccess() ? get() : supplier.get();
    }

    /**
     * Returns the underlying value if present, otherwise throws a user-specific exception.
     *
     * @param exceptionProvider provides a user-specific exception
     * @param <X>               exception type
     * @return A value of type {@code T}
     * @throws X                    if this is a {@code Failure}
     * @throws NullPointerException if the given {@code exceptionProvider} is null
     */
    public <X extends Throwable> T getOrElseThrow(Function<? super Throwable, ? extends X> exceptionProvider) throws X {
        Objects.requireNonNull(exceptionProvider, "exceptionProvider is null");
        if (isSuccess()) {
            return get();
        } else {
            throw exceptionProvider.apply(getCause());
        }
    }

    /**
     * Checks if this is a Failure.
     *
     * @return true, if this is a Failure, otherwise false, if this is a Success
     */
    public abstract boolean isFailure();

    /**
     * Checks if this is a Success.
     *
     * @return true, if this is a Success, otherwise false, if this is a Failure
     */
    public abstract boolean isSuccess();

    @Override
    public Iterator<T> iterator() {
        return isSuccess() ? Iterator.of(get()) : Iterator.empty();
    }

    /**
     * Runs the given checked function if this is a {@code Success},
     * passing the result of the current expression to it.
     * If this expression is a {@code Failure} then it'll return a new
     * {@code Failure} of type R with the original exception.
     * <p>
     * The main use case is chaining checked functions using method references:
     *
     * <pre>
     * <code>
     * Try.of(() -&gt; 0)
     *    .map(x -&gt; 1 / x); // division by zero
     * </code>
     * </pre>
     *
     * @param <U>    The new component type
     * @param mapper A checked function
     * @return a {@code Try}
     * @throws NullPointerException if {@code mapper} is null
     */
    @SuppressWarnings("unchecked")
    public <U> Try<U> map(CheckedFunction<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (isSuccess()) {
            try {
                return success(mapper.apply(get()));
            } catch (Throwable t) {
                return failure(t);
            }
        } else {
            return (Try<U>) this;
        }
    }

    /**
     * Maps the cause to a new exception if this is a {@code Failure} or returns this instance if this is a {@code Success}.
     *
     * @param mapper A function that maps the cause of a failure to another exception.
     * @return A new {@code Try} if this is a {@code Failure}, otherwise this.
     * @throws NullPointerException if the given {@code mapper} is null
     */
    public Try<T> mapFailure(CheckedFunction<? super Throwable, ? extends Throwable> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        if (isFailure()) {
            try {
                return failure(mapper.apply(getCause()));
            } catch (Throwable t) {
                return failure(t);
            }
        } else {
            return this;
        }
    }

    /**
     * Consumes the cause if this is a {@link Try.Failure} and the cause is instance of the given {@code exceptionType}.
     *
     * <pre>{@code
     * // (does not print anything)
     * Try.success(1).onFailure(Error.class, System.out::println);
     *
     * // prints "java.lang.Error"
     * Try.failure(new Error()).onFailure(Error.class, System.out::println);
     *
     * // (does not print anything)
     * Try.failure(new Exception()).onFailure(Error.class, System.out::println);
     * }</pre>
     *
     * @param <X>           Exception type
     * @param exceptionType The specific exception type that should be handled
     * @param action        An exception consumer
     * @return this
     * @throws NullPointerException if one of {@code exceptionType} or {@code action} is null
     */
    public <X extends Throwable> Try<T> onFailure(Class<X> exceptionType, Consumer<? super Throwable> action) {
        Objects.requireNonNull(exceptionType, "exceptionType is null");
        Objects.requireNonNull(action, "action is null");
        if (isFailure() && exceptionType.isInstance(getCause())) {
            action.accept(getCause());
        }
        return this;
    }

    /**
     * Consumes the cause if this is a {@link Try.Failure}.
     *
     * <pre>{@code
     * // (does not print anything)
     * Try.success(1).onFailure(System.out::println);
     *
     * // prints "java.lang.Error"
     * Try.failure(new Error()).onFailure(System.out::println);
     * }</pre>
     *
     * @param action An exception consumer
     * @return this
     * @throws NullPointerException if {@code action} is null
     */
    public Try<T> onFailure(Consumer<? super Throwable> action) {
        Objects.requireNonNull(action, "action is null");
        if (isFailure()) {
            action.accept(getCause());
        }
        return this;
    }

    /**
     * Consumes the value if this is a {@link Try.Success}.
     *
     * <pre>{@code
     * // prints "1"
     * Try.success(1).onSuccess(System.out::println);
     *
     * // (does not print anything)
     * Try.failure(new Error()).onSuccess(System.out::println);
     * }</pre>
     *
     * @param action A value consumer
     * @return this
     * @throws NullPointerException if {@code action} is null
     */
    public Try<T> onSuccess(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action is null");
        if (isSuccess()) {
            action.accept(get());
        }
        return this;
    }

    /**
     * Returns this {@code Try} in the case of a {@code Success}, otherwise {@code other.call()}.
     *
     * @param supplier a {@link CheckedSupplier}
     * @return a {@code Try} instance
     */
    @SuppressWarnings("unchecked")
    public Try<T> orElse(CheckedSupplier<? extends Try<? extends T>> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        if (isSuccess()) {
            return this;
        } else {
            try {
                return (Try<T>) supplier.get();
            } catch (Throwable x) {
                return failure(x);
            }
        }
    }

    /**
     * Returns {@code this}, if this is a {@code Success} or this is a {@code Failure} and the cause is not assignable
     * from {@code cause.getClass()}.
     * <p>
     * Otherwise tries to recover the exception of the failure with {@code recoveryFunction}.
     *
     * <pre>{@code
     * // = Success(13)
     * Try.of(() -> 27/2).recover(ArithmeticException.class, x -> Integer.MAX_VALUE);
     *
     * // = Success(2147483647)
     * Try.of(() -> 1/0)
     *    .recover(Error.class, x -> -1)
     *    .recover(ArithmeticException.class, x -> Integer.MAX_VALUE);
     *
     * // = Failure(java.lang.ArithmeticException: / by zero)
     * Try.of(() -> 1/0).recover(Error.class, x -> Integer.MAX_VALUE);
     * }</pre>
     *
     * @param <X>              Exception type
     * @param exceptionType    The specific exception type that should be handled
     * @param recoveryFunction A recovery function taking an exception of type {@code X}
     * @return a {@code Try} instance
     * @throws NullPointerException if {@code exceptionType} is null or {@code recoveryFunction} is null
     */
    @SuppressWarnings("unchecked")
    public <X extends Throwable> Try<T> recover(Class<X> exceptionType, CheckedFunction<? super X, ? extends T> recoveryFunction) {
        Objects.requireNonNull(exceptionType, "exceptionType is null");
        Objects.requireNonNull(recoveryFunction, "recoveryFunction is null");
        if (isFailure()) {
            final Throwable cause = getCause();
            if (exceptionType.isInstance(cause)) {
                return Try.of(() -> recoveryFunction.apply((X) cause));
            }
        }
        return this;
    }

    /**
     * Returns {@code this}, if this is a {@code Success} or this is a {@code Failure} and the cause is not assignable
     * from {@code cause.getClass()}. Otherwise tries to recover the exception of the failure with {@code recoveryFunction} <b>which returns Try</b>.
     * If {@link Try#isFailure()} returned by {@code recoveryFunction} function is <code>true</code> it means that recovery cannot take place due to some circumstances.
     *
     * <pre>{@code
     * // = Success(13)
     * Try.of(() -> 27/2).recoverWith(ArithmeticException.class, x -> Try.success(Integer.MAX_VALUE));
     *
     * // = Success(2147483647)
     * Try.of(() -> 1/0)
     *    .recoverWith(Error.class, x -> Try.success(-1))
     *    .recoverWith(ArithmeticException.class, x -> Try.success(Integer.MAX_VALUE));
     *
     * // = Failure(java.lang.ArithmeticException: / by zero)
     * Try.of(() -> 1/0).recoverWith(Error.class, x -> Try.success(Integer.MAX_VALUE));
     * }</pre>
     *
     * @param <X>              Exception type
     * @param exceptionType    The specific exception type that should be handled
     * @param recoveryFunction A recovery function taking an exception of type {@code X} and returning Try as a result of recovery.
     *                         If Try is {@link Try#isSuccess()} then recovery ends up successfully. Otherwise the function was not able to recover.
     * @return a {@code Try} instance
     * @throws NullPointerException if {@code exceptionType} or {@code recoveryFunction} is null
     * @throws Error                if the given recovery function {@code recoveryFunction} throws a fatal error
     */
    @SuppressWarnings("unchecked")
    public <X extends Throwable> Try<T> recoverWith(Class<X> exceptionType, CheckedFunction<? super X, ? extends Try<? extends T>> recoveryFunction) {
        Objects.requireNonNull(exceptionType, "exceptionType is null");
        Objects.requireNonNull(recoveryFunction, "recoveryFunction is null");
        if (isFailure()) {
            final Throwable cause = getCause();
            if (exceptionType.isInstance(cause)) {
                try {
                    return (Try<T>) recoveryFunction.apply((X) cause);
                } catch (Throwable t) {
                    return failure(t);
                }
            }
        }
        return this;
    }

    /**
     * Rethrows the underlying cause if this is a {@code Failure} and the cause is instance of the given {@code exceptionType}.
     *
     * @param exceptionType The specific exception type that should be handled
     * @param <X>           Exception type
     * @return              this instance, if this a {@code Success}
     * @throws X            if this is a {@code Failure} and the cause is instance of {@code X}
     * @throws NullPointerException if the given {@code exceptionType} is null
     */
    @SuppressWarnings("unchecked")
    public <X extends Throwable> Try<T> rethrow(Class<X> exceptionType) throws X {
        Objects.requireNonNull(exceptionType, "exceptionType is null");
        if (isFailure() && exceptionType.isInstance(getCause())) {
            throw (X) getCause();
        } else {
            return this;
        }
    }

    /**
     * Converts this {@code Try} to a {@link Stream}.
     *
     * @return {@code Stream.of(get()} if this is a success, otherwise {@code Stream.empty()}
     */
    public Stream<T> stream() {
        return isSuccess() ? Stream.of(get()) : Stream.empty();
    }

    /**
     * Converts this {@code Try} to an {@link Either}.
     *
     * @param <U> the left type of the {@code Either}
     * @param failureMapper a failure mapper
     * @return {@code Either.right(get()} if this is a success, otherwise {@code Either.left(failureMapper.apply(getCause())}
     * @throws NullPointerException if the given {@code failureMapper} is null
     */
    public <U> Either<U, T> toEither(Function<? super Throwable, ? extends U> failureMapper) {
        Objects.requireNonNull(failureMapper, "failureMapper is null");
        return isSuccess() ? Either.right(get()) : Either.left(failureMapper.apply(getCause()));
    }

    /**
     * Converts this {@code Try} to an {@link Option}.
     *
     * @return {@code Option.some(get()} if this is a success, otherwise {@code Option.none()}
     */
    public Option<T> toOption() {
        return isSuccess() ? Option.some(get()) : Option.none();
    }

    /**
     * Converts this {@code Try} to an {@link Optional}.
     *
     * @return {@code Optional.ofNullable(get())} if this is a success, otherwise {@code Optional.empty()}
     */
    public Optional<T> toOptional() {
        return isSuccess() ? Optional.ofNullable(get()) : Optional.empty();
    }

    /**
     * Transforms this {@code Try} by applying either {@code ifSuccess} to this value or {@code ifFailure} to this cause.
     *
     * @param ifFailure maps the cause if this is a {@code Failure}
     * @param ifSuccess maps the value if this is a {@code Success}
     * @param <U>       type of the transformed value
     * @return A new {@code Try} instance
     * @throws NullPointerException if one of the given {@code ifSuccess} or {@code ifFailure} is null
     */
    @SuppressWarnings("unchecked")
    public <U> Try<U> transform(CheckedFunction<? super Throwable, ? extends Try<? extends U>> ifFailure, CheckedFunction<? super T, ? extends Try<? extends U>> ifSuccess) {
        Objects.requireNonNull(ifFailure, "ifFailure is null");
        Objects.requireNonNull(ifSuccess, "ifSuccess is null");
        try {
            return isSuccess()
                   ? (Try<U>) ifSuccess.apply(get())
                   : (Try<U>) ifFailure.apply(getCause());
        } catch (Throwable t) {
            return failure(t);
        }
    }

    /**
     * Checks if this {@code Try} is equal to the given object {@code o}.
     * 
     * @param that an object, may be null
     * @return true, if {@code this} and {@code that} both are a success and the underlying values are equal
     *         or if {@code this} and {@code that} both are a failure and the underlying causes refer to the same object.
     *         Otherwise it returns false.
     */
    @Override
    public abstract boolean equals(Object that);

    /**
     * Computes the hash of this {@code Try}.
     *
     * @return {@code 31 + Objects.hashCode(get())} if this is a success, otherwise {@code Objects.hashCode(getCause())}
     */
    @Override
    public abstract int hashCode();

    /**
     * Returns a string representation of this {@code Try}.
     *
     * @return {@code "Success(" + get() + ")"} if this is a success, otherwise {@code "Failure(" + getCause() + ")"}
     */
    @Override
    public abstract String toString();

    /**
     * A succeeded Try.
     *
     * @param <T> component type of this Success
     */
    private static final class Success<T> extends Try<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        private final T value;

        /**
         * Constructs a Success.
         *
         * @param value The value of this Success.
         */
        private Success(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public Throwable getCause() {
            throw new UnsupportedOperationException("getCause() on Success");
        }

        @Override
        public boolean isFailure() {
            return false;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj == this) || (obj instanceof Success && Objects.equals(value, ((Success<?>) obj).value));
        }

        @Override
        public int hashCode() {
            return 31 + Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return "Success(" + value + ")";
        }
    }

    /**
     * A failed Try. It represents an exceptional state.
     * <p>
     * The cause of type {@code Throwable} is internally stored for further processing.
     *
     * @param <T> component type of this Failure
     */
    private static final class Failure<T> extends Try<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        private final Throwable cause;

        /**
         * Constructs a Failure.
         *
         * @param cause                 A cause of type Throwable, may be null.
         * @throws NullPointerException if {@code cause} is null
         * @throws Error                if the given {@code cause} is fatal, i.e. non-recoverable
         */
        private Failure(Throwable cause) {
            if (cause instanceof LinkageError || cause instanceof ThreadDeath || cause instanceof VirtualMachineError) {
                throw (Error) cause;
            }
            this.cause = cause;
        }

        @Override
        public T get() throws Error, RuntimeException {
            if (cause == null) {
                throw new NullPointerException();
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new NonFatalException(cause);
            }
        }

        @Override
        public Throwable getCause() {
            return cause;
        }

        @Override
        public boolean isFailure() {
            return true;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj == this) || (obj instanceof Failure && Objects.equals(((Failure<?>) obj).cause, cause));
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(cause);
        }

        @Override
        public String toString() {
            return "Failure(" + cause + ")";
        }

    }
}
