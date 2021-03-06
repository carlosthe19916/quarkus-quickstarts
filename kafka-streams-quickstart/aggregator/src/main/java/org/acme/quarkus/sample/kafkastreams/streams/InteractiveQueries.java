package org.acme.quarkus.sample.kafkastreams.streams;

import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.acme.quarkus.sample.kafkastreams.model.Aggregation;
import org.acme.quarkus.sample.kafkastreams.model.WeatherStationData;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.StreamsMetadata;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class InteractiveQueries {

    private static final Logger LOG = LoggerFactory.getLogger(InteractiveQueries.class);

    @ConfigProperty(name="hostname")
    String host;

    @Inject
    KafkaStreams streams;

    public List<PipelineMetadata> getMetaData() {
        return streams.allMetadataForStore(TopologyProducer.WEATHER_STATIONS_STORE)
                .stream()
                .map(m -> new PipelineMetadata(
                        m.hostInfo().host() + ":" + m.hostInfo().port(),
                        m.topicPartitions()
                            .stream()
                            .map(TopicPartition::toString)
                            .collect(Collectors.toSet()))
                )
                .collect(Collectors.toList());
    }

    public GetWeatherStationDataResult getWeatherStationData(int id) {
        StreamsMetadata metadata = streams.metadataForKey(
                TopologyProducer.WEATHER_STATIONS_STORE,
                id,
                Serdes.Integer().serializer()
        );

        if (metadata == null || metadata == StreamsMetadata.NOT_AVAILABLE) {
            LOG.warn("Found no metadata for key {}", id);
            return GetWeatherStationDataResult.notFound();
        }
        else if (metadata.host().equals(host)) {
            LOG.info("Found data for key {} locally", id);
            Aggregation result = getWeatherStationStore().get(id);

            if (result != null) {
                return GetWeatherStationDataResult.found(WeatherStationData.from(result));
            }
            else {
                return GetWeatherStationDataResult.notFound();
            }
        }
        else {
            LOG.info("Found data for key {} on remote host {}:{}", id, metadata.host(), metadata.port());
            return GetWeatherStationDataResult.foundRemotely(metadata.host(), metadata.port());
        }
    }

    private ReadOnlyKeyValueStore<Integer, Aggregation> getWeatherStationStore() {
        while (true) {
            try {
                return streams.store(TopologyProducer.WEATHER_STATIONS_STORE, QueryableStoreTypes.keyValueStore());
            } catch (InvalidStateStoreException e) {
                // ignore, store not ready yet
            }
        }
    }
}
