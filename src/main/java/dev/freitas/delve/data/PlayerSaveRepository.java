package dev.freitas.delve.data;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerSaveRepository extends CrudRepository<PlayerSave, Long> {}
