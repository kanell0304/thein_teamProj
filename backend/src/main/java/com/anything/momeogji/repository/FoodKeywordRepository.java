package com.anything.momeogji.repository;

import com.anything.momeogji.entity.FoodKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodKeywordRepository extends JpaRepository<FoodKeyword, Long> {

    List<FoodKeyword> findAllByOrderByIdAsc();
}
