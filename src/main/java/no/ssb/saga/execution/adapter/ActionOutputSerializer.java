package no.ssb.saga.execution.adapter;

public interface ActionOutputSerializer<V> {

    Class<V> serializationClazz();

    String serialize(V data);

    V deserialize(String serializedData);
}
