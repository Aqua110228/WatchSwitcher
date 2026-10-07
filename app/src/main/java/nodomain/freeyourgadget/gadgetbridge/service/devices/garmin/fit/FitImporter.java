/*  Copyright (C) 2024-2026 José Rebelo, CaptKentish, Daniele Gobbetti, Thomas Kuehne

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.BaseActivitySummaryProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.BatteryLevelProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminBodyEnergySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSolarChargeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminHeartRateRestingSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminHrvSummarySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminHrvValueSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminIntensityMinutesSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminNapSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminRespiratoryRateSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminRestingMetabolicRateSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSleepRestlessMomentsSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSleepStageSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSleepStatsSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSpo2SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GarminStressSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericTrainingLoadAcuteSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericTrainingLoadChronicSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.PersistenceProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminActivitySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminEventSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminWorkoutParser;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.BatteryLevel;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminBodyEnergySample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSolarChargeSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEventSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminHeartRateRestingSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminHrvSummarySample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminHrvValueSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminIntensityMinutesSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminNapSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminRespiratoryRateSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminRestingMetabolicRateSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSleepRestlessMomentsSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSleepStageSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSleepStatsSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSpo2Sample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminStressSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericMetricSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadAcuteSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadChronicSample;
import nodomain.freeyourgadget.gadgetbridge.export.AutoFitExporter;
import nodomain.freeyourgadget.gadgetbridge.export.AutoGpxExporter;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.model.FitActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.FileType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminUtils;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Event;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.EventType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.HrvStatus;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.SleepLevel;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.exception.FitParseException;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitBattery;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitDiveReadiness;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitEnduranceScore;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitFileId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitFunctionalMetrics;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitHillScore;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitHrvStatusSummary;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitHrvValue;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitMaxMetData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitMetricRecovery;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitMonitoring;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitMonitoringHrData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitMonitoringInfo;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitNapEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitPad;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitPhysiologicalMetrics;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitRacePrediction;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitRecord;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitRespirationRate;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSession;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSleepAssessment;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSleepDataInfo;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSleepDataRaw;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSleepLevel;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSleepRestlessMoments;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSpo2Data;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSolarCharge;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitStressLevel;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitTimeInZone;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitTrainingLoad;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitTrainingReadiness;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitUserProfile;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class FitImporter {
    private static final Logger LOG = LoggerFactory.getLogger(FitImporter.class);
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);

    private final Context context;
    private final GBDevice gbDevice;

    private final SortedMap<Long, List<FitMonitoring>> activitySamplesPerTimestamp = new TreeMap<>();
    private final List<GarminStressSample> stressSamples = new ArrayList<>();
    private final List<GarminBodyEnergySample> bodyEnergySamples = new ArrayList<>();
    private final List<GarminSolarChargeSample> solarChargeSamples = new ArrayList<>();
    private final List<GarminSpo2Sample> spo2samples = new ArrayList<>();
    private final List<GarminRespiratoryRateSample> respiratoryRateSamples = new ArrayList<>();
    private final List<GarminHeartRateRestingSample> restingHrSamples = new ArrayList<>();
    private final List<GarminEventSample> events = new ArrayList<>();
    private final List<GarminSleepStatsSample> sleepStatsSamples = new ArrayList<>();
    private final List<GarminSleepStageSample> sleepStageSamples = new ArrayList<>();
    private final List<GarminNapSample> napSamples = new ArrayList<>();
    private final List<GarminHrvSummarySample> hrvSummarySamples = new ArrayList<>();
    private final List<GarminHrvValueSample> hrvValueSamples = new ArrayList<>();
    private final List<GarminRestingMetabolicRateSample> restingMetabolicRateSamples = new ArrayList<>();
    private final List<GenericTrainingLoadAcuteSample> trainingLoadAcuteSamples = new ArrayList<>();
    private final List<GenericTrainingLoadChronicSample> trainingLoadChronicSamples = new ArrayList<>();
    private final Map<Integer, Integer> unknownRecords = new HashMap<>();
    @Nullable
    private FitSleepDataInfo fitSleepDataInfo = null;
    private final List<FitSleepDataRaw> fitSleepDataRawSamples = new ArrayList<>();
    private final List<GarminSleepRestlessMomentsSample> sleepRestlessMomentsSamples = new ArrayList<>();
    private final List<BatteryLevel> batterySamples = new ArrayList<>();
    @Nullable
    private FitFileId fileId = null;
    private final List<GenericMetricSample> genericMetricSamples = new ArrayList<>();

    private final GarminWorkoutParser workoutParser;

    public FitImporter(final Context context, final GBDevice gbDevice) {
        this.context = context;
        this.gbDevice = gbDevice;
        this.workoutParser = new GarminWorkoutParser(context);
    }

    /**
     * @noinspection StatementWithEmptyBody
     */
    public void importFile(@NonNull final File file, final boolean isReprocessing) throws IOException, FitParseException {
        LOG.debug("Importing {}", file.getAbsolutePath());

        reset();

        final FitFile fitFile = FitFile.parseIncoming(file);

        Long lastMonitoringTimestamp = null;

        for (final RecordData record : fitFile.getRecords()) {
            if (fileId != null && fileId.getType() == FileType.FILETYPE.ACTIVITY) {
                if (workoutParser.handleRecord(record)) {
                    continue;
                }
            }

            final Long ts = record.getComputedTimestamp();

            if (record instanceof FitFileId newFileId) {
                LOG.debug("File ID: {}", newFileId);
                if (fileId != null) {
                    // Should not happen
                    LOG.warn("Already had a file ID: {}", fileId);
                }
                fileId = newFileId;
            } else if (record instanceof FitStressLevel stressRecord) {
                final Integer stress = stressRecord.getStressLevelValue();
                if (stress != null && stress >= 0) {
                    LOG.trace("Stress at {}: {}", ts, stress);
                    final GarminStressSample sample = new GarminStressSample();
                    sample.setTimestamp(ts * 1000L);
                    sample.setStress(stress);
                    stressSamples.add(sample);
                }

                final Integer energy = stressRecord.getBodyEnergy();
                if (energy != null) {
                    LOG.trace("Body energy at {}: {}", ts, energy);
                    final GarminBodyEnergySample sample = new GarminBodyEnergySample();
                    sample.setTimestamp(ts * 1000L);
                    sample.setEnergy(energy);
                    bodyEnergySamples.add(sample);
                }
            } else if (record instanceof FitSolarCharge solarChargeRecord) {
                addSolarChargeSample(ts, solarChargeRecord.getPercent(), solarChargeRecord.getGain(), solarChargeSamples);
            } else if (record instanceof FitSleepDataInfo newFitSleepDataInfo) {
                LOG.debug("Sleep Data Info: {}", newFitSleepDataInfo);
                if (fitSleepDataInfo != null) {
                    // Should not happen
                    LOG.warn("Already had sleep data info: {}", fitSleepDataInfo);
                }
                fitSleepDataInfo = newFitSleepDataInfo;
            } else if (record instanceof FitSleepDataRaw fitSleepDataRaw) {
                //LOG.debug("Sleep Data Raw: {}", fitSleepDataRaw);
                fitSleepDataRawSamples.add(fitSleepDataRaw);
            } else if (record instanceof FitSleepRestlessMoments fitSleepRestlessMoments) {
                //LOG.debug("Sleep Restless Moments: {}", fitSleepRestlessMoments);
                if (fitSleepRestlessMoments.getRestlessMomentsCount() != null) {
                    final GarminSleepRestlessMomentsSample sample = new GarminSleepRestlessMomentsSample();
                    sample.setTimestamp(ts * 1000L);
                    sample.setCount(fitSleepRestlessMoments.getRestlessMomentsCount());
                    sleepRestlessMomentsSamples.add(sample);
                }
            } else if (record instanceof FitSleepAssessment fitSleepAssessment) {
                final Integer score = fitSleepAssessment.getOverallSleepScore();
                if (score == null) {
                    continue;
                }
                LOG.trace("Sleep stats at {}: {}", ts, fitSleepAssessment);
                final GarminSleepStatsSample sample = new GarminSleepStatsSample();
                sample.setTimestamp(ts * 1000L);
                sample.setSleepScore(score);
                sleepStatsSamples.add(sample);
            } else if (record instanceof FitSleepLevel fitSleepLevel) {
                final SleepLevel stage = fitSleepLevel.getSleepLevel();
                if (stage == null) {
                    continue;
                }
                LOG.trace("Sleep stage at {}: {}", ts, fitSleepLevel);
                final GarminSleepStageSample sample = new GarminSleepStageSample();
                sample.setTimestamp(ts * 1000L);
                sample.setStage(stage.num);
                sleepStageSamples.add(sample);
            } else if (record instanceof FitNapEvent nap) {
                if (nap.getStartTime() == null || nap.getEndTime() == null) {
                    continue;
                }
                LOG.trace("Nap at {}: from {} to {}", ts, nap.getStartTime(), nap.getEndTime());
                final GarminNapSample sample = new GarminNapSample();
                sample.setTimestamp(nap.getStartTime() * 1000L);
                sample.setEndTimestamp(nap.getEndTime() * 1000L);
                napSamples.add(sample);
            } else if (record instanceof FitMonitoring monitoringRecord) {
                final Long currentMonitoringTimestamp = monitoringRecord.computeTimestamp(lastMonitoringTimestamp);
                LOG.trace(
                        "Monitoring at {}: {}",
                        SDF.format(new Date(currentMonitoringTimestamp * 1000L)),
                        monitoringRecord
                );
                if (!activitySamplesPerTimestamp.containsKey(currentMonitoringTimestamp)) {
                    activitySamplesPerTimestamp.put(currentMonitoringTimestamp, new ArrayList<>());
                }
                Objects.requireNonNull(activitySamplesPerTimestamp.get(currentMonitoringTimestamp)).add(monitoringRecord);
                lastMonitoringTimestamp = currentMonitoringTimestamp;
                addMetric(currentMonitoringTimestamp, monitoringRecord.getTotalAscent(), MetricSample.Metric.DAILY_TOTAL_ASCENT);
                addMetric(currentMonitoringTimestamp, monitoringRecord.getTotalDescent(), MetricSample.Metric.DAILY_TOTAL_DESCENT);
            } else if (record instanceof FitSpo2Data fitSpo2) {
                final Integer spo2 = fitSpo2.getReadingSpo2();
                if (spo2 == null || spo2 <= 0) {
                    continue;
                }
                LOG.trace("SpO2 at {}: {}", ts, spo2);
                final GarminSpo2Sample sample = new GarminSpo2Sample();
                sample.setTimestamp(ts * 1000L);
                sample.setSpo2(spo2);
                sample.setTypeNum(Spo2Sample.Type.UNKNOWN.getNum());
                if (fitSpo2.getMode() != null) {
                    switch (fitSpo2.getMode()) {
                        case 1:
                            sample.setTypeNum(Spo2Sample.Type.MANUAL.getNum());
                            break;
                        case 3:
                            sample.setTypeNum(Spo2Sample.Type.AUTOMATIC.getNum());
                            break;
                    }
                }
                spo2samples.add(sample);
            } else if (record instanceof FitRespirationRate fitRespirationRate) {
                final Float respiratoryRate = fitRespirationRate.getRespirationRate();
                if (respiratoryRate == null || respiratoryRate <= 0) {
                    continue;
                }
                LOG.trace("Respiratory rate at {}: {}", ts, respiratoryRate);
                final GarminRespiratoryRateSample sample = new GarminRespiratoryRateSample();
                sample.setTimestamp(ts * 1000L);
                sample.setRespiratoryRate(respiratoryRate);
                respiratoryRateSamples.add(sample);
            } else if (record instanceof FitEvent event) {
                if (event.getEvent() == null) {
                    LOG.warn("Event in {} is null", event);
                    continue;
                } else if (ts == null) {
                    LOG.warn("Timestamp for {} is null", event);
                    continue;
                }

                LOG.trace("Event at {}: {}", ts, event);

                final GarminEventSample sample = new GarminEventSample();
                sample.setTimestamp(ts * 1000L);
                sample.setEvent(event.getEvent());
                if (event.getEventType() != null) {
                    sample.setEventType(event.getEventType());
                }
                if (event.getData() != null) {
                    sample.setData(event.getData());
                }
                events.add(sample);
            } else if (record instanceof FitRecord) {
                // handled in workout parser
            } else if (record instanceof FitSession) {
                // handled in workout parser
            } else if (record instanceof FitPhysiologicalMetrics) {
                // handled in workout parser
            } else if (record instanceof FitSport) {
                // handled in workout parser
            } else if (record instanceof FitTimeInZone) {
                // handled in workout parser
            } else if (record instanceof FitUserProfile) {
                // handled in workout parser
            } else if (record instanceof FitHrvStatusSummary hrvSummary) {
                LOG.trace("HRV summary at {}: {}", ts, hrvSummary);
                final GarminHrvSummarySample sample = new GarminHrvSummarySample();
                sample.setTimestamp(ts * 1000L);
                if (hrvSummary.getWeeklyAverage() != null) {
                    sample.setWeeklyAverage(Math.round(hrvSummary.getWeeklyAverage()));
                }
                if (hrvSummary.getLastNightAverage() != null) {
                    sample.setLastNightAverage(Math.round(hrvSummary.getLastNightAverage()));
                }
                if (hrvSummary.getLastNight5MinHigh() != null) {
                    sample.setLastNight5MinHigh(Math.round(hrvSummary.getLastNight5MinHigh()));
                }
                if (hrvSummary.getBaselineLowUpper() != null) {
                    sample.setBaselineLowUpper(Math.round(hrvSummary.getBaselineLowUpper()));
                }
                if (hrvSummary.getBaselineBalancedLower() != null) {
                    sample.setBaselineBalancedLower(Math.round(hrvSummary.getBaselineBalancedLower()));
                }
                if (hrvSummary.getBaselineBalancedUpper() != null) {
                    sample.setBaselineBalancedUpper(Math.round(hrvSummary.getBaselineBalancedUpper()));
                }
                final HrvStatus status = hrvSummary.getStatus();
                if (status != null) {
                    sample.setStatusNum(status.num);
                }
                hrvSummarySamples.add(sample);
            } else if (record instanceof FitHrvValue hrvValue) {
                if (hrvValue.getValue() == null) {
                    LOG.warn("HRV value at {} is null", ts);
                    continue;
                }
                LOG.trace("HRV value at {}: {}", ts, hrvValue.getValue());
                final GarminHrvValueSample sample = new GarminHrvValueSample();
                sample.setTimestamp(ts * 1000L);
                sample.setValue(Math.round(hrvValue.getValue()));
                hrvValueSamples.add(sample);
            } else if (record instanceof FitMonitoringInfo monitoringInfo) {
                if (monitoringInfo.getRestingMetabolicRate() == null) {
                    continue;
                }
                LOG.trace("Monitoring info at {}: {}", ts, monitoringInfo);
                final GarminRestingMetabolicRateSample sample = new GarminRestingMetabolicRateSample();
                sample.setTimestamp(ts * 1000L);
                sample.setRestingMetabolicRate(monitoringInfo.getRestingMetabolicRate());
                restingMetabolicRateSamples.add(sample);
            } else if (record instanceof FitTrainingLoad trainingLoad) {
                LOG.trace("Training load at {}: {}", ts, trainingLoad);
                if (trainingLoad.getTrainingLoadAcute() != null) {
                    final GenericTrainingLoadAcuteSample sample = new GenericTrainingLoadAcuteSample();
                    sample.setTimestamp(ts * 1000L);
                    sample.setValue(trainingLoad.getTrainingLoadAcute());
                    trainingLoadAcuteSamples.add(sample);
                }
                if (trainingLoad.getTrainingLoadChronic() != null) {
                    final GenericTrainingLoadChronicSample sample = new GenericTrainingLoadChronicSample();
                    sample.setTimestamp(ts * 1000L);
                    sample.setValue(trainingLoad.getTrainingLoadChronic());
                    trainingLoadChronicSamples.add(sample);
                }
            } else if (record instanceof FitRacePrediction racePrediction) {
                LOG.trace("Race prediction at {}: {}", ts, racePrediction);
                addMetric(ts, racePrediction.getTime5k(), MetricSample.Metric.GENERIC_RACE_PREDICTOR_5K);
                addMetric(ts, racePrediction.getTime10k(), MetricSample.Metric.GENERIC_RACE_PREDICTOR_10K);
                addMetric(ts, racePrediction.getTimeHalfMarathon(), MetricSample.Metric.GENERIC_RACE_PREDICTOR_HALF_MARATHON);
                addMetric(ts, racePrediction.getTimeFullMarathon(), MetricSample.Metric.GENERIC_RACE_PREDICTOR_FULL_MARATHON);
            } else if (record instanceof FitMonitoringHrData monitoringHrData) {
                if (monitoringHrData.getRestingHeartRate() == null && monitoringHrData.getCurrentDayRestingHeartRate() == null) {
                    LOG.warn("Resting HR at {} is null", ts);
                    continue;
                }
                LOG.trace("Resting HR at {}: {}, currentDay={}", ts, monitoringHrData.getRestingHeartRate(), monitoringHrData.getCurrentDayRestingHeartRate());
                final GarminHeartRateRestingSample sample = new GarminHeartRateRestingSample();
                sample.setTimestamp(ts * 1000L);
                if (monitoringHrData.getCurrentDayRestingHeartRate() != null) {
                    // Prioritize the current day value - that matches what the watch displays
                    sample.setHeartRate(monitoringHrData.getCurrentDayRestingHeartRate());
                } else {
                    sample.setHeartRate(monitoringHrData.getRestingHeartRate());
                }
                restingHrSamples.add(sample);
            } else if (record instanceof FitBattery fitBattery) {
                Integer remainingCapacity = fitBattery.getCapacity();
                if (ts != null && remainingCapacity != null) {
                    BatteryLevel batteryLevel = new BatteryLevel();
                    batteryLevel.setTimestamp(ts.intValue());
                    batteryLevel.setBatteryIndex(0);
                    batteryLevel.setLevel(remainingCapacity);
                    batterySamples.add(batteryLevel);
                }
            } else if (record instanceof FitHillScore fitHillScore) {
                final Integer hillEndurance = fitHillScore.getHillEndurance();
                final Integer hillScore = fitHillScore.getHillScore();
                final Integer hillStrength = fitHillScore.getHillStrength();
                final Integer level = fitHillScore.getLevel();
                addMetric(ts, hillEndurance, MetricSample.Metric.GARMIN_HILL_ENDURANCE, level);
                addMetric(ts, hillScore, MetricSample.Metric.GARMIN_HILL_SCORE, level);
                addMetric(ts, hillStrength, MetricSample.Metric.GARMIN_HILL_STRENGTH, level);
            } else if (record instanceof FitTrainingReadiness fitTrainingReadiness) {
                final Integer trainingReadiness = fitTrainingReadiness.getTrainingReadiness();
                final Integer level = fitTrainingReadiness.getLevel();
                addMetric(ts, trainingReadiness, MetricSample.Metric.GARMIN_TRAINING_READINESS, level);
            } else if (record instanceof FitMetricRecovery fitMetricRecovery) {
                final Integer recoveryMinutes = fitMetricRecovery.getRecoveryMinutes();
                // Unlike most GenericMetricSample sources here, 0 is a real, common, confirmed
                // reading for this metric ("fully recovered"), not an absence-of-data sentinel.
                if (recoveryMinutes != null && recoveryMinutes >= 0) {
                    final GenericMetricSample sample = new GenericMetricSample();
                    sample.setTimestamp(ts * 1000L);
                    sample.setMetric(MetricSample.Metric.GARMIN_RECOVERY_TIME, recoveryMinutes);
                    genericMetricSamples.add(sample);
                }
            } else if (record instanceof FitEnduranceScore fitEnduranceScore) {
                final Integer enduranceScore = fitEnduranceScore.getEnduranceScore();
                final Integer level = fitEnduranceScore.getLevel();
                addMetric(ts, enduranceScore, MetricSample.Metric.GARMIN_ENDURANCE_SCORE, level);
            } else if (record instanceof FitFunctionalMetrics fitFunctionalMetrics) {
                final Integer cyclingLactaceThresholdHr = fitFunctionalMetrics.getCyclingLactaceThresholdHr();
                final Integer functionalThresholdPower = fitFunctionalMetrics.getFunctionalThresholdPower();
                addMetric(ts, functionalThresholdPower, MetricSample.Metric.GARMIN_FUNCTIONAL_THRESHOLD_POWER, cyclingLactaceThresholdHr);

                final Integer runningLactateThresholdHr = fitFunctionalMetrics.getRunningLactateThresholdHr();
                final Integer runningLactateThresholdPower = fitFunctionalMetrics.getRunningLactateThresholdPower();
                addMetric(ts, runningLactateThresholdPower, MetricSample.Metric.GARMIN_RUNNING_LACTATE_THRESHOLD_POWER, runningLactateThresholdHr);
            } else if (record instanceof FitMaxMetData fitMaxMetData) {
                final Float vo2Max = fitMaxMetData.getVo2Max();
                final Integer maxMetCategory = fitMaxMetData.getMaxMetCategory();
                addMetric(ts, vo2Max, MetricSample.Metric.GARMIN_MET_MAX_VO2, maxMetCategory);
            } else if (record instanceof FitDiveReadiness fitDiveReadiness) {
                final Integer diveReadiness = fitDiveReadiness.getDiveReadiness();
                addMetric(ts, diveReadiness, MetricSample.Metric.GARMIN_DIVE_READINESS);
            } else if (record instanceof FitPad) {
                // nothing to do - this is just a spacer
            } else {
                LOG.trace("Unknown record: {}", record);

                if (!unknownRecords.containsKey(record.getNativeFITMessage().getNumber())) {
                    unknownRecords.put(record.getNativeFITMessage().getNumber(), 0);
                }
                unknownRecords.put(
                        record.getNativeFITMessage().getNumber(),
                        Objects.requireNonNull(unknownRecords.get(record.getNativeFITMessage().getNumber())) + 1
                );
            }
        }

        if (fileId == null) {
            LOG.error("Got no file ID");
            return;
        }
        if (fileId.getType() == null) {
            LOG.error("File has no type");
            return;
        }

        // If the file is not yet on the export directory (e.g. we're importing from phone storage), copy it
        File finalExportFile = file;
        try {
            final File exportDirectory = gbDevice.getDeviceCoordinator().getWritableExportDirectory(gbDevice, true);
            if (!file.getAbsolutePath().startsWith(exportDirectory.getAbsolutePath())) {
                final File exportFile = new File(exportDirectory, getFilePath(fileId));
                if (exportFile.isFile()) {
                    // Prevent overwrite
                    LOG.warn("Fit file {} already exists as {}", file, exportFile);
                } else {
                    LOG.debug("Copying {} to {}", file, exportFile);
                    final File parentFile = exportFile.getParentFile();
                    if (parentFile != null) {
                        //noinspection ResultOfMethodCallIgnored
                        parentFile.mkdirs();
                    }
                    FileUtils.copyFile(file, exportFile);
                    //noinspection ResultOfMethodCallIgnored
                    exportFile.setLastModified(file.lastModified());
                }

                finalExportFile = exportFile;
            }
        } catch (final Exception e) {
            LOG.error("Failed to copy file to export directory", e);
        }

        try (DBHandler handler = GBApplication.acquireDB()) {
            final DaoSession session = handler.getDaoSession();

            // specific rules
            switch (fileId.getType()) {
                case ACTIVITY:
                    persistWorkout(finalExportFile, session, isReprocessing, fitFile);
                    break;
                case MONITOR:
                    persistActivitySamples(session);
                    break;
                case METRICS:
                    break;
                case DEVICE_58:
                    break;
                case SLEEP:
                    // We may have samples, but not sleep samples - #4048
                    // 0 unmeasurable, 1 awake
                    final boolean anySleepSample = sleepStageSamples.stream()
                            .anyMatch(s -> s.getStage() != 0 && s.getStage() != 1);
                    if (anySleepSample) {
                        persistAbstractSamples(sleepStageSamples, new GarminSleepStageSampleProvider(gbDevice, session));
                    }

                    processRawSleepSamples(session);
                    break;
                case HRV_STATUS:
                    break;
                default:
                    LOG.warn("No specific rules for FIT files of type {}", fileId.getType());
            }

            // some of these samples can occur in multiple FIT file types
            persistAbstractSamples(batterySamples, new BatteryLevelProvider(gbDevice, session));
            persistAbstractSamples(bodyEnergySamples, new GarminBodyEnergySampleProvider(gbDevice, session));
            persistAbstractSamples(events, new GarminEventSampleProvider(gbDevice, session));
            persistAbstractSamples(hrvSummarySamples, new GarminHrvSummarySampleProvider(gbDevice, session));
            persistAbstractSamples(hrvValueSamples, new GarminHrvValueSampleProvider(gbDevice, session));
            persistAbstractSamples(napSamples, new GarminNapSampleProvider(gbDevice, session));
            persistAbstractSamples(respiratoryRateSamples, new GarminRespiratoryRateSampleProvider(gbDevice, session));
            persistAbstractSamples(restingHrSamples, new GarminHeartRateRestingSampleProvider(gbDevice, session));
            persistAbstractSamples(restingMetabolicRateSamples, new GarminRestingMetabolicRateSampleProvider(gbDevice, session));
            persistAbstractSamples(sleepRestlessMomentsSamples, new GarminSleepRestlessMomentsSampleProvider(gbDevice, session));
            persistAbstractSamples(sleepStatsSamples, new GarminSleepStatsSampleProvider(gbDevice, session));
            persistAbstractSamples(solarChargeSamples, new GarminSolarChargeSampleProvider(gbDevice, session));
            persistAbstractSamples(spo2samples, new GarminSpo2SampleProvider(gbDevice, session));
            persistAbstractSamples(stressSamples, new GarminStressSampleProvider(gbDevice, session));
            persistAbstractSamples(trainingLoadAcuteSamples, new GenericTrainingLoadAcuteSampleProvider(gbDevice, session));
            persistAbstractSamples(trainingLoadChronicSamples, new GenericTrainingLoadChronicSampleProvider(gbDevice, session));
            persistMetricSamples(session);
        } catch (final Exception e) {
            GB.toast(context, "Error saving FIT samples", Toast.LENGTH_LONG, GB.ERROR, e);
        }

        for (final Map.Entry<Integer, Integer> e : unknownRecords.entrySet()) {
            final String NativeMessageNumber = FitDebug.mesgNumLookup(e.getKey());
            if (NativeMessageNumber.contains("_")) {
                LOG.info("Unhandled FIT record of native number {} seen {} times", NativeMessageNumber, e.getValue());
            } else {
                LOG.warn("Unknown FIT record of native number {} seen {} times", NativeMessageNumber, e.getValue());
            }
        }
    }

    void addMetric(@Nullable final Long ts, @Nullable final Number value, @NonNull final MetricSample.Metric metric) {
        addMetric(ts, value, metric, (Long) null);
    }

    void addMetric(@Nullable final Long ts, @Nullable final Number value, @NonNull final MetricSample.Metric metric, @Nullable Integer extra) {
        Long longExtra = (extra == null) ? null : extra.longValue();
        addMetric(ts, value, metric, longExtra);
    }

    void addMetric(@Nullable final Long ts, @Nullable final Number value, @NonNull final MetricSample.Metric metric, @Nullable Long extra) {
        if (ts == null || value == null) {
            return;
        }
        double v = value.doubleValue();
        if (v <= 0.0 || Double.isNaN(v)) {
            return;
        }
        final GenericMetricSample sample = new GenericMetricSample();
        sample.setTimestamp(ts * 1000L);
        sample.setMetric(metric, v, extra);
        genericMetricSamples.add(sample);
    }

    static void addSolarChargeSample(@Nullable final Long ts, @Nullable final Float percent, @Nullable final Long gain, final List<GarminSolarChargeSample> out) {
        if (ts == null || percent == null || percent < 0) {
            return;
        }
        final GarminSolarChargeSample sample = new GarminSolarChargeSample();
        sample.setTimestamp(ts * 1000L);
        sample.setPercent(percent);
        sample.setGain(gain != null ? gain : 0L);
        out.add(sample);
    }

    private void persistMetricSamples(@NonNull final DaoSession session) {
        // During the transition phase some samples are persisted twice:
        // 1) to the old individual tables (see importFile)
        // 2) to the new generic metrics table (here)
        // this enables thorough testing without risking data loss.

        for (final GenericTrainingLoadAcuteSample legacy : trainingLoadAcuteSamples) {
            GenericMetricSample metric = new GenericMetricSample();
            metric.setTimestamp(legacy.getTimestamp());
            metric.setMetric(MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE, legacy.getValue());
            genericMetricSamples.add(metric);
        }

        for (final GenericTrainingLoadChronicSample legacy : trainingLoadChronicSamples) {
            final GenericMetricSample metric = new GenericMetricSample();
            metric.setTimestamp(legacy.getTimestamp());
            metric.setMetric(MetricSample.Metric.GENERIC_TRAINING_LOAD_CHRONIC, legacy.getValue());
            genericMetricSamples.add(metric);
        }

        for (final GarminRestingMetabolicRateSample legacy : restingMetabolicRateSamples) {
            final GenericMetricSample metric = new GenericMetricSample();
            metric.setTimestamp(legacy.getTimestamp());
            metric.setMetric(MetricSample.Metric.GENERIC_RESTING_METABOLIC_RATE, legacy.getRestingMetabolicRate());
            genericMetricSamples.add(metric);
        }

        final GenericMetricSampleProvider provider = new GenericMetricSampleProvider(gbDevice, session);
        persistAbstractSamples(genericMetricSamples, provider);
    }

    private void persistWorkout(final File file, final DaoSession session, boolean isReprocessing, FitFile fitFile) {
        LOG.debug("Persisting workout for {}", fileId);

        Long sessionStart = workoutParser.getSessionStartTime();
        Long timeCreated = fileId.getTimeCreated();
        BaseActivitySummary summary = null;

        // This ensures idempotency when re-processing
        if (isReprocessing && timeCreated != null) {
            try {
                summary = ActivitySummaryParser.findBaseActivitySummary(
                        session,
                        gbDevice,
                        timeCreated
                );

                if (summary != null && sessionStart != null) {
                    summary.setStartTime(new Date(sessionStart * 1000L));
                }
            } catch (final Exception e) {
                LOG.error("Error finding existing base summary", e);
                // continue to findOrCreateBaseActivitySummary
            }
        }

        if (summary == null) {
            final Long start = (sessionStart == null) ? timeCreated : sessionStart;

            try {
                summary = ActivitySummaryParser.findOrCreateBaseActivitySummary(
                        session,
                        gbDevice,
                        Objects.requireNonNull(start)
                );
            } catch (final Exception e) {
                GB.toast(context, "Error finding base summary", Toast.LENGTH_LONG, GB.ERROR, e);
                return;
            }
        }

        workoutParser.updateSummary(summary);
        final GenericMetricSampleProvider provider = new GenericMetricSampleProvider(gbDevice, session);
        persistAbstractSamples(workoutParser.getGenericMetricSamples(), provider);

        summary.setRawDetailsPath(file.getAbsolutePath());

        try {
            persistAbstractSamples(List.of(summary), new BaseActivitySummaryProvider(gbDevice, session));
        } catch (final Exception e) {
            GB.toast(context, "Error saving workout", Toast.LENGTH_LONG, GB.ERROR, e);
        }

        // Export to gpx / fit
        if (AutoGpxExporter.isExportEnabled(gbDevice) || AutoFitExporter.isExportEnabled(gbDevice)) {
            final FitActivityTrackProvider activityTrackProvider = new FitActivityTrackProvider();
            final ActivityTrack activityTrack = activityTrackProvider.getActivityTrack(summary, fitFile);
            if (activityTrack != null) {
                AutoGpxExporter.doExport(context, gbDevice, summary, activityTrack);
                AutoFitExporter.doExport(context, gbDevice, summary, activityTrack);
            }
        }
    }

    private void reset() {
        activitySamplesPerTimestamp.clear();
        stressSamples.clear();
        bodyEnergySamples.clear();
        solarChargeSamples.clear();
        spo2samples.clear();
        respiratoryRateSamples.clear();
        restingHrSamples.clear();
        events.clear();
        sleepStatsSamples.clear();
        sleepStageSamples.clear();
        napSamples.clear();
        hrvSummarySamples.clear();
        hrvValueSamples.clear();
        restingMetabolicRateSamples.clear();
        trainingLoadAcuteSamples.clear();
        trainingLoadChronicSamples.clear();
        unknownRecords.clear();
        fitSleepDataInfo = null;
        fitSleepDataRawSamples.clear();
        sleepRestlessMomentsSamples.clear();
        batterySamples.clear();
        fileId = null;
        workoutParser.reset();
        genericMetricSamples.clear();
    }

    private void persistActivitySamples(final DaoSession session) {
        if (activitySamplesPerTimestamp.isEmpty()) {
            return;
        }

        final List<GarminActivitySample> activitySamples = new ArrayList<>(activitySamplesPerTimestamp.size());
        final List<GarminIntensityMinutesSample> intensityMinutesSamples = new ArrayList<>(activitySamplesPerTimestamp.size());

        // Garmin reports the cumulative data per activity, but not always, so we need to keep
        // track of the amounts for each activity, and set the sum of all on the sample
        final Map<Integer, Long> stepsPerActivity = new HashMap<>();
        final Map<Integer, Long> distancePerActivity = new HashMap<>();
        final Map<Integer, Integer> caloriesPerActivity = new HashMap<>();

        final int THRESHOLD_NOT_WORN = 10 * 60; // 10 min gap between samples = not-worn
        int prevActivityKind = ActivityKind.UNKNOWN.getCode();
        int prevTs = -1;

        for (final long ts : activitySamplesPerTimestamp.keySet()) {
            if (prevTs > 0 && ts - prevTs > 60) {
                // Fill gaps between samples
                LOG.debug(
                        "Filling gap between {} and {}",
                        SDF.format(new Date(prevTs * 1000L)),
                        SDF.format(new Date(ts * 1000L))
                );
                for (int i = prevTs + 60; i < ts; i += 60) {
                    final GarminActivitySample sample = new GarminActivitySample();
                    sample.setTimestamp(i);
                    sample.setRawKind(ts - prevTs > THRESHOLD_NOT_WORN ? ActivityKind.NOT_WORN.getCode() : prevActivityKind);
                    sample.setRawIntensity(ActivitySample.NOT_MEASURED);
                    sample.setSteps(ActivitySample.NOT_MEASURED);
                    sample.setHeartRate(ActivitySample.NOT_MEASURED);
                    sample.setDistanceCm(ActivitySample.NOT_MEASURED);
                    sample.setActiveCalories(ActivitySample.NOT_MEASURED);
                    activitySamples.add(sample);
                }
            }

            final List<FitMonitoring> records = activitySamplesPerTimestamp.get(ts);

            final GarminActivitySample sample = new GarminActivitySample();
            sample.setTimestamp((int) ts);
            sample.setRawKind(ActivityKind.ACTIVITY.getCode());
            sample.setRawIntensity(ActivitySample.NOT_MEASURED);
            sample.setSteps(ActivitySample.NOT_MEASURED);
            sample.setHeartRate(ActivitySample.NOT_MEASURED);
            sample.setDistanceCm(ActivitySample.NOT_MEASURED);
            sample.setActiveCalories(ActivitySample.NOT_MEASURED);

            int minutesModerate = 0;
            int minutesVigorous = 0;

            for (final FitMonitoring record : Objects.requireNonNull(records)) {
                final Integer activityType = record.getComputedActivityType().orElse(ActivitySample.NOT_MEASURED);

                final Integer hr = record.getHeartRate();
                if (hr != null) {
                    sample.setHeartRate(hr);
                }

                final Long steps = record.getCycles();
                if (steps != null) {
                    stepsPerActivity.put(activityType, steps);
                }

                final Long distance = record.getDistance();
                if (distance != null) {
                    distancePerActivity.put(activityType, distance);
                }

                final Integer calories = record.getActiveCalories();
                if (calories != null) {
                    caloriesPerActivity.put(activityType, calories);
                }

                final Integer intensity = record.getComputedIntensity();
                if (intensity != null) {
                    sample.setRawIntensity(intensity);
                }

                final Integer recordMinutesModerate = record.getModerateActivityMinutes();
                if (recordMinutesModerate != null) {
                    minutesModerate += recordMinutesModerate;
                }

                final Integer recordMinutesVigorous = record.getVigorousActivityMinutes();
                if (recordMinutesVigorous != null) {
                    minutesVigorous += recordMinutesVigorous;
                }
            }
            if (!stepsPerActivity.isEmpty()) {
                int sumSteps = 0;
                for (final Long steps : stepsPerActivity.values()) {
                    sumSteps += steps;
                }
                sample.setSteps(sumSteps);
            }
            if (!distancePerActivity.isEmpty()) {
                int sumDistance = 0;
                for (final Long distance : distancePerActivity.values()) {
                    sumDistance += distance;
                }
                sample.setDistanceCm(sumDistance);
            }
            if (!caloriesPerActivity.isEmpty()) {
                int sumCalories = 0;
                for (final Integer calories : caloriesPerActivity.values()) {
                    sumCalories += calories;
                }
                sample.setActiveCalories(sumCalories);
            }

            // Ignore empty samples
            if (sample.getRawIntensity() != ActivitySample.NOT_MEASURED ||
                    sample.getSteps() != ActivitySample.NOT_MEASURED ||
                    sample.getHeartRate() != ActivitySample.NOT_MEASURED ||
                    sample.getDistanceCm() != ActivitySample.NOT_MEASURED ||
                    sample.getActiveCalories() != ActivitySample.NOT_MEASURED) {
                activitySamples.add(sample);
                prevActivityKind = sample.getRawKind();
                prevTs = (int) ts;
            } else {
                LOG.debug("Ignoring empty sample at {}", sample.getTimestamp());
            }

            if (minutesModerate != 0 || minutesVigorous != 0) {
                final GarminIntensityMinutesSample intensityMinutesSample = new GarminIntensityMinutesSample();
                intensityMinutesSample.setTimestamp(ts * 1000L);
                intensityMinutesSample.setModerate(minutesModerate);
                intensityMinutesSample.setVigorous(minutesVigorous);
                intensityMinutesSamples.add(intensityMinutesSample);
            }
        }

        try {
            persistAbstractSamples(activitySamples, new GarminActivitySampleProvider(gbDevice, session));
        } catch (final Exception e) {
            GB.toast(context, "Error saving activity samples", Toast.LENGTH_LONG, GB.ERROR, e);
        }

        try {
            persistAbstractSamples(intensityMinutesSamples, new GarminIntensityMinutesSampleProvider(gbDevice, session));
        } catch (final Exception e) {
            GB.toast(context, "Error saving intensity minutes samples", Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    /**
     * As per #4048, devices that do not have a sleep widget send raw sleep samples, which we do not
     * know how to parse. Therefore, we don't persist the sleep stages they report (they're all awake),
     * but we fake light sleep for the duration of the raw sleep samples, in order to have some data
     * at all.
     */
    private void processRawSleepSamples(final DaoSession session) {
        if (fitSleepDataRawSamples.isEmpty()) {
            return;
        }

        final boolean anySleepSample = sleepStageSamples.stream()
                .anyMatch(s -> s.getStage() != 0 && s.getStage() != 1);
        if (anySleepSample) {
            // We have at least one real sleep sample - do nothing
            return;
        }

        final long asleepTimeMillis = Objects.requireNonNull(fileId.getTimeCreated()).intValue() * 1000L;
        final long wakeTimeMillis = asleepTimeMillis + fitSleepDataRawSamples.size() * 60 * 1000L;

        LOG.debug("Got {} raw sleep samples - faking sleep events from {} to {}", fitSleepDataRawSamples.size(), asleepTimeMillis, wakeTimeMillis);

        // We only need to fake sleep start and end times, the sample provider will take care of the rest
        try {
            final GarminEventSampleProvider sampleProvider = new GarminEventSampleProvider(gbDevice, session);

            final GarminEventSample sampleFallAsleep = new GarminEventSample();
            sampleFallAsleep.setTimestamp(asleepTimeMillis);
            sampleFallAsleep.setEvent(Event.DETECT_SLEEP); // sleep
            sampleFallAsleep.setEventType(EventType.START); // sleep start
            sampleFallAsleep.setData(-1L); // in actual samples they're a garmin epoch, this way we can identify them

            final GarminEventSample sampleWakeUp = new GarminEventSample();
            sampleWakeUp.setTimestamp(wakeTimeMillis);
            sampleWakeUp.setEvent(Event.DETECT_SLEEP); // sleep
            sampleWakeUp.setEventType(EventType.STOP); // sleep end
            sampleWakeUp.setData(-1L); // in actual samples they're a garmin epoch, this way we can identify them

            persistAbstractSamples(List.of(sampleFallAsleep, sampleWakeUp), sampleProvider);
        } catch (final Exception e) {
            GB.toast(context, "Error faking event samples", Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    private <T> void persistAbstractSamples(@NonNull final List<T> samples,
                                            @NonNull final PersistenceProvider<T> sampleProvider) {
        sampleProvider.persistSamples(samples, context);
    }

    public static String getFilePath(final FitFileId fileId) {
        // FitFileId.getTimeCreated() is a boxed Long because the FIT
        // schema marks the field optional; null-check before unboxing.
        final Date created = fileId.getTimeCreated() != null
                ? new Date(fileId.getTimeCreated() * 1000L)
                : null;
        return GarminUtils.buildExportPath(fileId.getType(), created, "", "fit");
    }
}
