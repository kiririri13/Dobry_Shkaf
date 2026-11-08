package ru.sfustudio.dobryshkaf.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sfustudio.dobryshkaf.domain.District;

import java.util.List;
import java.util.UUID;

public interface DistrictRepository extends JpaRepository<District, UUID> {

    List<District> findAllByOrderByCityAscNameAsc();
}
