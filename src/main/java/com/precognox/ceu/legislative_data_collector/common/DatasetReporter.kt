package com.precognox.ceu.legislative_data_collector.common

import com.precognox.ceu.legislative_data_collector.entities.Amendment
import com.precognox.ceu.legislative_data_collector.entities.Committee
import com.precognox.ceu.legislative_data_collector.entities.Country
import com.precognox.ceu.legislative_data_collector.entities.ImpactAssessment
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage
import com.precognox.ceu.legislative_data_collector.entities.Originator
import com.precognox.ceu.legislative_data_collector.entities.colombia.ColombiaOriginatorVariables
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository
import com.precognox.ceu.legislative_data_collector.utils.TextUtils
import lombok.extern.slf4j.Slf4j
import org.springframework.stereotype.Service
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.List
import javax.persistence.Column
import javax.persistence.EntityManager
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

@Slf4j
@Service
class DatasetReporter(
    private val pageSourceRepository: PageSourceRepository,
    private val entityManager: EntityManager) {

    private lateinit var reportFile: File

    fun printReport() {
        println("Generating report from dataset...")

        val exportFolderPath: String = Constants.getExportFolder()
        Files.createDirectories(Path.of(exportFolderPath))

        reportFile = File("$exportFolderPath/report.txt")
        reportFile.writeText("") //clear file

        findUnprocessedBills()
        printBillStatusDistribution()

        val entityClasses = List.of(LegislativeDataRecord::class, Amendment::class, ImpactAssessment::class)
        val embeddedClasses = List.of(Committee::class, LegislativeStage::class, Originator::class)

        reportFile.appendText("Collecting statistics...\n\n")

        //simplify code and solve with native query for entities also?
        entityClasses.forEach { entityClass ->
            val entityName = entityClass.simpleName
            val countQuery = "SELECT COUNT(s) FROM $entityName s"

            val totalRecords = entityManager
                    .createQuery(countQuery, java.lang.Long::class.java)
                    .singleResult
                    .toLong()

            reportFile.appendText("Found $totalRecords records for entity $entityName\n")

            val fieldCounts = calculateFieldCountsForEntity(entityClass)
            val fieldPercentages = calculatePercentages(fieldCounts, totalRecords)

            printStatistics(fieldPercentages, entityName!!)
        }

        embeddedClasses.forEach { embeddedClass ->
            val tableName = tableNameFromEntity(embeddedClass)

            val totalRecords: BigInteger = entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM $tableName")
                    .singleResult as BigInteger

            reportFile.appendText("Found ${totalRecords} records in table $tableName\n")

            val fieldCounts = calculateFieldCountsForEmbeddedType(embeddedClass)
            val fieldPercentages = calculatePercentages(fieldCounts, totalRecords.longValueExact())
            printStatistics(fieldPercentages, tableName)
        }

        println("Done generating report, see ${reportFile.path}")
    }

    private fun findUnprocessedBills() {
        reportFile.appendText("Checking for unprocessed page sources...\n")

        val unprocessedBillLinks = pageSourceRepository.findUnprocessedBills(Country.HUNGARY)

        if (unprocessedBillLinks.isNotEmpty()) {
            reportFile.appendText("Found ${unprocessedBillLinks.size} unprocessed bill pages:\n")
            unprocessedBillLinks.forEach { bill -> reportFile.appendText("\t - $bill\n") }
        } else {
            reportFile.appendText("Found none\n\n")
        }
    }

    private fun printBillStatusDistribution() {
        val countQuery = "SELECT COUNT(*) FROM bill_main_table"
        val groupByQuery = "SELECT bill_status, COUNT(*) FROM bill_main_table GROUP BY bill_status"

        val totalRecords = entityManager.createNativeQuery(countQuery).singleResult as BigInteger
        val groups = entityManager.createNativeQuery(groupByQuery).resultList

        reportFile.appendText("Bill status distribution:\n")

        groups.forEach { result: Any? ->
            if (result is Array<*>) {
                val status = result[0]
                val count = result[1] as BigInteger

                val percentage = count.toBigDecimal()
                    .divide(totalRecords.toBigDecimal(), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))

                reportFile.appendText(" - $status: $count ($percentage%)\n")
            }
        }

        reportFile.appendText("\n")
    }

    private fun calculateFieldCountsForEntity(entityClass: KClass<out Any>): MutableMap<String, Long> {
        val fieldCounts: MutableMap<String, Long> = mutableMapOf()
        val properties = entityClass.declaredMemberProperties
        val collectionTypes = listOf(List::class.starProjectedType, Set::class.starProjectedType)

        properties.forEach { property ->
            run {
                val qStr: String = if (collectionTypes.none(property.returnType::isSubtypeOf)) {
                    "SELECT COUNT(r) FROM ${entityClass.simpleName} r WHERE r.${property.name} IS NOT NULL"
                } else {
                    "SELECT COUNT(r) FROM ${entityClass.simpleName} r WHERE r.${property.name} IS NOT EMPTY"
                }

                val count = entityManager.createQuery(qStr).singleResult
                fieldCounts.putIfAbsent(property.name, count as Long)
            }
        }

        return fieldCounts
    }

    private fun calculateFieldCountsForEmbeddedType(embeddedClass: KClass<out Any>): MutableMap<String, Long> {
        val tableName = tableNameFromEntity(embeddedClass)
        val fieldCounts: MutableMap<String, Long> = mutableMapOf()
        val properties = embeddedClass.declaredMemberProperties
        val typesToSkip = listOf(ColombiaOriginatorVariables::class.createType()) //or skip by name

        properties.forEach { property ->
            run {
                if (typesToSkip.none(property.returnType::isSubtypeOf)) {
                    //get column name from annotation, or convert field name to snake case is annotation is not present
                    val columnAnnotation = property.findAnnotation<Column>()
                    val colName = columnAnnotation?.name ?: TextUtils.convertCamelCaseToSnakeCase(property.name)

                    val qStr = "SELECT COUNT(*) FROM $tableName WHERE $colName IS NOT NULL"
                    val count: BigInteger = entityManager.createNativeQuery(qStr).singleResult as BigInteger
                    fieldCounts.putIfAbsent(property.name, count.longValueExact())
                }
            }
        }

        return fieldCounts
    }

    //make plural and snake case
    private fun tableNameFromEntity(embeddedClass: KClass<out Any>) =
            TextUtils.convertCamelCaseToSnakeCase(embeddedClass.simpleName!!).lowercase() + "s"

    private fun calculatePercentages(
            fieldCounts: MutableMap<String, Long>, totalRecords: Long): MutableMap<String, Double> {
        val fieldPercentages: MutableMap<String, Double> = mutableMapOf()

        fieldCounts.keys.forEach { fieldName ->
            fieldPercentages[fieldName] = fieldCounts.getOrDefault(fieldName, 0) / totalRecords.toDouble()
        }

        return fieldPercentages
    }

    private fun printStatistics(fieldPercentages: MutableMap<String, Double>, tableName: String) {
        reportFile.appendText("Percentages of variables filled for '${tableName}': \n")

        fieldPercentages.entries.sortedBy { it.key }.forEach { entry ->
            reportFile.appendText("${entry.key}: ${formatPercentage(entry.value)}\n")
            //System.out.format("%30s:%10s%n", entry.key, formatPercentage(entry.value))
        }

        reportFile.appendText("\n")
    }

    private fun formatPercentage(d: Double) = String.format("%.2f", d * 100) + "%"

}
