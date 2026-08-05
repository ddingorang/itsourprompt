package com.promptstudio.pricing.repository;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.time.Instant;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * model_price의 jOOQ 상수.
 *
 * <p>단가를 쓰는 쪽이 둘이라 public이다 — 이 패키지가 표를 채우고, 랭킹 쿼리가 그 표를 조인한다.
 * 각자 컬럼을 선언하면 이름을 바꿀 때 컴파일러가 잡아주지 않는 짝이 하나 더 생긴다.
 */
public final class ModelPriceTables {

    public static final Table<?> MODEL_PRICE = table(name("model_price"));
    public static final Field<String> MODEL = field(name("model_price", "model"), SQLDataType.VARCHAR);
    public static final Field<BigDecimal> INPUT = field(name("model_price", "input"), SQLDataType.DECIMAL);
    /** NULL이면 입력 단가를 적용한다. */
    public static final Field<BigDecimal> CACHED_INPUT =
            field(name("model_price", "cached_input"), SQLDataType.DECIMAL);
    public static final Field<BigDecimal> OUTPUT = field(name("model_price", "output"), SQLDataType.DECIMAL);
    public static final Field<Instant> UPDATED_AT = field(name("model_price", "updated_at"), SQLDataType.INSTANT);

    private ModelPriceTables() {
    }
}
