package com.github.amchang.ohdsi.lib

import java.nio.file.{Files, Paths}

import com.github.nscala_time.time.Imports._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SQLContext}


/**
  * Replication of functionality in
  *
  * https://github.com/OHDSI/Era-Constructor/blob/master/v5/PostgreSQL/postgres_v5_dose_era.sql
  */
class DoseEra(implicit sparkCont: SparkContext, conf: Config = ConfigFactory.load()) extends DrugExposure {

  /**
    * Override these from the trait extends
    */
  protected val sparkContext = sparkCont
  protected val sqlContext = new SQLContext(sparkContext)
  protected val config = conf

  /**
    * Store the most recent build
    */
  private var mostRecentBuild: RDD[(PersonId, DrugConceptId, UnitConceptId, DoseValue, DrugExposureStartDate, DrugExposureEndDate)] = null

  /**
    * Build Dosage Eras
    */
  def build: RDD[(PersonId, DrugConceptId, UnitConceptId, DoseValue, DrugExposureStartDate, DrugExposureEndDate)] = {
    // the entire data
    val bareData = createInitialData()

    // nothing return nothing
    if (bareData.count == 0) {
      return sparkContext.emptyRDD
    }

    mostRecentBuild = bareData
      .reduceByKey(_ ++ _)
      .map {
        // at this rate we have all of the dates for a particular key
        case ((personId, drugConceptId, unitConceptId, doseValue), dateList) =>
          ((personId, drugConceptId, unitConceptId, doseValue), Era.rangeBuilder(dateList))
      }
      .flatMap {
        // find the count of net ranges
        case (((personId, drugConceptId, unitConceptId, doseValue), finalCombine)) =>
          finalCombine.map {
            case ((firstDate, secondDate), count, noStockpile, stockpile)  =>
              // differs slightly from project, no count
              (personId, drugConceptId, unitConceptId, doseValue, firstDate, secondDate)
        }
      }
      // get rid of duplicates
      .distinct
      .map{
        // flatten out everything with the count
        case (personId, drugConceptId, unitConceptId, doseValue, firstDate, secondDate) =>
          (personId, drugConceptId, unitConceptId, doseValue, firstDate, secondDate)
      }
      .sortBy {
        // sort by person id, conditionConceptId, and startDateEra desc
        case (personId, drugConceptId, unitConceptId, doseValue, startDate, endDate) =>
          (personId, drugConceptId, startDate.getMillis * -1)
      }
      .cache

    mostRecentBuild
  }

  /**
    * Write the result of the most recent build
    */
  override def writeCSV(): Option[String] =  {
  /*  val format = sparkContext.broadcast(config.getString("ohdsi.dateFormat"))
    val rowRdd = mostRecentBuild.zipWithIndex.map {
      case ((personId, drugConceptId, unitConceptId, doseValue, startDate, endDate), index) =>
        Row(index.toString, personId.toString, conditionConceptId.toString, startDate.toString(format.value),
          endDate.toString(format.value), count.toString)
    }.sortBy(_.getString(0))

    sqlContext.createDataFrame(rowRdd, StructType(List(
        StructField("condition_occurrence_id", StringType, true),
        StructField("person_id", StringType, true),
        StructField("condition_concept_id", StringType, true),
        StructField("condition_era_start_date", StringType, true),
        StructField("condition_era_end_date", StringType, true),
        StructField("condition_occurrence_count", StringType, true)
      )))
      .write
      .format("com.databricks.spark.csv")
      .option("header", "true")
      .save(s"${config.getString("ohdsi.csv.location")}dose_era_${System.currentTimeMillis()}")*/
    Some(null)
  }

}
