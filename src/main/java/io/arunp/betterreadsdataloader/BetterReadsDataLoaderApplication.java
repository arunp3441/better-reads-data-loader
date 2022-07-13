package io.arunp.betterreadsdataloader;

import io.arunp.betterreadsdataloader.author.Author;
import io.arunp.betterreadsdataloader.author.AuthorRepository;
import io.arunp.betterreadsdataloader.book.Book;
import io.arunp.betterreadsdataloader.book.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
public class BetterReadsDataLoaderApplication {

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private BookRepository bookRepository;

	@Value("${datadump.location.author}")
	private String authorDumpLocation;

	@Value("${datadump.location.works}")
	private String worksDumpLocation;

	public static void main(String[] args) {
		SpringApplication.run(BetterReadsDataLoaderApplication.class, args);
	}

	@PostConstruct
	public void start(){
		initAuthors();
		initWorks();
	}

	private void initAuthors(){
		Path path = Paths.get(authorDumpLocation);
		try(Stream<String> lines = Files.lines(path)){
			lines.forEach(line -> {
				String jsonString = line.substring(line.indexOf("{"));
				try {
					JSONObject jsonObject = new JSONObject(jsonString);
					Author author = new Author();
					author.setId(jsonObject.optString("key").replace("/authors/",""));
					author.setName(jsonObject.optString("name"));
					author.setPersonalName(jsonObject.optString("personal_name"));
					authorRepository.save(author);
				} catch (JSONException jsonException) {
					jsonException.printStackTrace();
				}
			});
		}
		catch(IOException ioException){
			ioException.printStackTrace();
		}
	}

	private void initWorks(){
		Path path = Paths.get(worksDumpLocation);
		try(Stream<String> lines = Files.lines(path)){
			lines.forEach(line -> {
				String jsonString = line.substring(line.indexOf("{"));
				try {
					JSONObject jsonObject = new JSONObject(jsonString);
					Book book = new Book();
					book.setId(jsonObject.optString("key").replace("/works/",""));
					book.setName(jsonObject.optString("title"));
					JSONObject descriptionObj = jsonObject.optJSONObject("description");
					if(descriptionObj != null) {
						book.setDescription(descriptionObj.optString("value"));
					}
					JSONArray coversArray = jsonObject.optJSONArray("covers");
					if(coversArray != null) {
						List<String> coverIds = new ArrayList<>();
						for(int i=0; i<coversArray.length() ; ++i ){
							coverIds.add(coversArray.getString(i));
						}
						book.setCoverIds(coverIds);
					}
					JSONArray authorsArray = jsonObject.optJSONArray("authors");
					if(authorsArray != null) {
						List<String> authorIds = new ArrayList<>();
						for(int i=0; i<authorsArray.length() ; ++i ){
							authorIds.add(authorsArray.getJSONObject(i).getJSONObject("author").optString("key").replace("/authors/",""));
						}
						book.setAuthorIds(authorIds);
						List<String> authorNames = authorIds.stream()
								.map(authorId -> authorRepository.findById(authorId)).flatMap(Optional::stream)
								.map(Author::getName).collect(Collectors.toList());
						book.setAuthorNames(authorNames);
					}
					JSONObject publishedObj = jsonObject.optJSONObject("created");
					if(publishedObj != null) {
						book.setPublishedDate(LocalDate.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(publishedObj.getString("value"))));
					}
					bookRepository.save(book);
				} catch (JSONException jsonException) {
					jsonException.printStackTrace();
				}
			});
		}
		catch(IOException ioException){
			ioException.printStackTrace();
		}
	}

	@Bean
	public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
		Path bundle = astraProperties.getSecureConnectBundle().toPath();
		return builder -> builder.withCloudSecureConnectBundle(bundle);
	}
}
