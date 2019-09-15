package ch.maxant.kdc.products;

import java.time.LocalDateTime;

public interface WithValidity {
    LocalDateTime getFrom();
    LocalDateTime getTo();

    public default boolean isOverlapping(WithValidity other) {
        // https://stackoverflow.com/a/17107966/458370

        // there is an overlap if:
        //
        //  |---A---|      existing
        // |--B--|         new
        //     |--B--|     new
        // |----B----|     new
        //   |--B--|       new

        //!start1.after(end2) && !start2.after(end1); => ie there is overlap if there is no mutual exclusiveness
        return !this.getFrom().isAfter(other.getTo()) && !other.getFrom().isAfter(this.getTo());

        //  |---this------------|
        //             |--other--|
        // |--other--|
        //     |--other--|
    }
}
