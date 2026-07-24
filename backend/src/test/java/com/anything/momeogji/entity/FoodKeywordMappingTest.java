package com.anything.momeogji.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class FoodKeywordMappingTest {

    @Test
    void mapsTheFourColumnFoodKeywordTableAndUniqueConstraint() throws Exception {
        Table table = FoodKeyword.class.getAnnotation(Table.class);

        assertThat(table.name()).isEqualTo("food_keywords");
        assertThat(table.uniqueConstraints()).singleElement().satisfies(constraint -> {
            assertThat(constraint.name()).isEqualTo("uk_food_keywords_type_name");
            assertThat(constraint.columnNames()).containsExactly("keyword_type", "name");
        });
        assertThat(FoodKeyword.class.getDeclaredFields())
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("id", "keywordType", "name", "aliases");
    }

    @Test
    void mapsKeywordTypeNameAndAliasesAsRequiredColumns() throws Exception {
        Field keywordType = FoodKeyword.class.getDeclaredField("keywordType");
        Field name = FoodKeyword.class.getDeclaredField("name");
        Field aliases = FoodKeyword.class.getDeclaredField("aliases");

        assertThat(keywordType.getAnnotation(Enumerated.class)).isNotNull();
        assertThat(keywordType.getAnnotation(Column.class)).satisfies(column -> {
            assertThat(column.name()).isEqualTo("keyword_type");
            assertThat(column.nullable()).isFalse();
            assertThat(column.length()).isEqualTo(20);
        });
        assertThat(name.getAnnotation(Column.class)).satisfies(column -> {
            assertThat(column.nullable()).isFalse();
            assertThat(column.length()).isEqualTo(100);
        });
        assertThat(aliases.getAnnotation(Column.class)).satisfies(column -> {
            assertThat(column.nullable()).isFalse();
            assertThat(column.columnDefinition()).isEqualTo("jsonb");
        });
        assertThat(aliases.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.JSON);
        assertThat(FoodKeyword.builder()
                .keywordType(FoodKeywordType.MENU)
                .name("초밥")
                .build()
                .getAliases()).isEmpty();
    }
}
