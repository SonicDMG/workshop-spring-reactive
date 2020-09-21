package org.springframework.samples.petclinic.visit.db;

import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.createTable;
import static org.springframework.samples.petclinic.visit.db.VisitEntity.VISIT_ATT_DESCRIPTION;
import static org.springframework.samples.petclinic.visit.db.VisitEntity.VISIT_ATT_PET_ID;
import static org.springframework.samples.petclinic.visit.db.VisitEntity.VISIT_ATT_VISIT_DATE;
import static org.springframework.samples.petclinic.visit.db.VisitEntity.VISIT_ATT_VISIT_ID;
import static org.springframework.samples.petclinic.visit.db.VisitEntity.VISIT_TABLE;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.pet.Pet;
import org.springframework.samples.petclinic.utils.MappingUtils;
import org.springframework.samples.petclinic.visit.Visit;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.dse.driver.api.mapper.reactive.MappedReactiveResultSet;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.mapper.annotations.Dao;
import com.datastax.oss.driver.api.mapper.annotations.Delete;
import com.datastax.oss.driver.api.mapper.annotations.Select;
import com.datastax.oss.driver.api.mapper.annotations.Update;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Definition of operations relative to table 'petclinic_visit_by_pet'. 
 * 
 * The DataStax Cassandra driver will generate the implementation at compile time.
 * More information can be found {@link https://docs.datastax.com/en/developer/java-driver/latest/manual/mapper/daos/select/#return-type}
 * 
 * @author Cedrick LUNVEN (@clunven)
 */
@Dao
public interface VisitReactiveDao {
    
    @Select
    MappedReactiveResultSet<VisitEntity> findAll();
    
    @Select(customWhereClause = VISIT_ATT_PET_ID + "= :petId")
    MappedReactiveResultSet<VisitEntity> findAllVisitsForAPet(UUID petId);
    
    @Select(customWhereClause = VISIT_ATT_VISIT_ID + "= :visitId", allowFiltering = true)
    MappedReactiveResultSet<VisitEntity> findByVisitById(UUID visitId);
    
    default void createSchemaVisit(CqlSession cqlSession) {
        
        /** 
         * CREATE TABLE IF NOT EXISTS petclinic_visit_by_pet (
         *  pet_id      uuid,
         *  visit_id    uuid,
         *  visit_date date,
         *  description text,
         *  PRIMARY KEY ((pet_id), visit_id));
         **/
        cqlSession.execute(
                createTable(VISIT_TABLE).ifNotExists()
                .withPartitionKey(VISIT_ATT_PET_ID, DataTypes.UUID)
                .withClusteringColumn(VISIT_ATT_VISIT_ID, DataTypes.UUID)
                .withColumn(VISIT_ATT_DESCRIPTION, DataTypes.TEXT)
                .withColumn(VISIT_ATT_VISIT_DATE, DataTypes.DATE)
                .build());
    }

    
    
    
    
    @Update
    ReactiveResultSet updateReactive(VisitEntity visit);
    default Mono<VisitEntity> save(VisitEntity visit) {
        return Mono.from(updateReactive(visit)).map(rr -> visit);
    }
    
    @Delete
    ReactiveResultSet deleteReactive(VisitEntity visit);
    default Mono<Boolean> delete(VisitEntity visit) {
        return Mono.from(deleteReactive(visit))
                   .map(rr -> rr.wasApplied());
    }
    
    default Mono<Pet> populateVisitsForPet(Pet wbp) {
        // Flux<Visit> 
        return Flux.from(findAllVisitsForAPet(wbp.getId()))
            // Flux<VisitWebBean>
            .map(MappingUtils::mapEntityAsVisit)
            // Mono<HashSet<Object>>
            .collect((Supplier<Set<Visit>>) HashSet::new, Set::add)
            // Populate input
            .doOnNext(wbp::setVisits)
            // return object populated
            .map(set -> wbp);
    }
    
    default Mono<Owner> populateVisitsForOwner(Owner wbo) {
        wbo.getPets().forEach(this::populateVisitsForPet);
        return Mono.just(wbo);
    }
    
    /**
     * Create objects required for this business domain (tables, index, udt) if they do not exist.
     */
    default void createSchemaPet(CqlSession cqlSession) {
        
    }
    
}
