package xyz.wagyourtail.jvmdg.internal.mods.stub._9;

import org.gradle.api.JavaVersion;
import xyz.wagyourtail.jvmdg.internal.mods.stub.Stub;

import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class J_U_S_Collectors {

    @Stub(value = JavaVersion.VERSION_1_9, desc = "Ljava/util/stream/Collectors;", include = FlatMappingCollector.class)
    public static <T, U, A, R> Collector<T, ?, R> flatMapping(
        Function<? super T, ? extends Stream<? extends U>> mapper,
        Collector<? super U, A, R> downstream) {
        BiConsumer<A, ? super U> downstreamAccumulator = downstream.accumulator();
        return (Collector<T, ?, R>) new FlatMappingCollector<>(mapper, downstream).get();
    }

    @Stub(value = JavaVersion.VERSION_1_9, desc = "Ljava/util/stream/Collectors;", include = FilteringCollector.class)
    public static <T, A, R> Collector<T, ?, R> filtering(Predicate<? super T> predicate, Collector<? super T, A, R> downstream) {
        return (Collector<T, ?, R>) new FilteringCollector<>(predicate, downstream).get();
    }

    public static class FlatMappingCollector<T, U, A, R>  {

        private final Function<? super T, ? extends Stream<? extends U>> mapper;
        private final Collector<? super U, A, R> downstream;

        public FlatMappingCollector(Function<? super T, ? extends Stream<? extends U>> mapper, Collector<? super U, A, R> downstream) {
            this.mapper = mapper;
            this.downstream = downstream;
        }

        public void accumulator(A a, T t) {
            try (Stream<? extends U> result = mapper.apply(t)) {
                if (result != null)
                    result.sequential().forEach(u -> downstream.accumulator().accept(a, u));
            }
        }

        public Collector<T, ?, R> get() {
            BiConsumer<A, ? super U> downstreamAccumulator = downstream.accumulator();
            return Collector.of(
                downstream.supplier(),
                this::accumulator,
                downstream.combiner(),
                downstream.finisher(),
                downstream.characteristics().toArray(new Collector.Characteristics[0])
            );
        }
    }

    public static class FilteringCollector<T, A, R> {
        private final Predicate<? super T> predicate;
        private final Collector<? super T, A, R> downstream;

        public FilteringCollector(Predicate<? super T> predicate, Collector<? super T, A, R> downstream) {
            this.predicate = predicate;
            this.downstream = downstream;
        }

        public void accumulator(A a, T t) {
            if (predicate.test(t)) {
                downstream.accumulator().accept(a, t);
            }
        }

        public Collector<T, ?, R> get() {
            return Collector.of(
                downstream.supplier(),
                this::accumulator,
                downstream.combiner(),
                downstream.finisher(),
                downstream.characteristics().toArray(new Collector.Characteristics[0])
            );
        }

    }
}
