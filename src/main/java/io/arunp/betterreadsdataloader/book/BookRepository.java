package io.arunp.betterreadsdataloader.book;

import org.springframework.data.cassandra.repository.CassandraRepository;

public interface BookRepository extends CassandraRepository<Book , String> {

}
