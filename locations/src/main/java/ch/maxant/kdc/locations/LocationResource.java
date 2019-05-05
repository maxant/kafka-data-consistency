package ch.maxant.kdc.locations;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("locations")
@ApplicationScoped
public class LocationResource {

    @GET
    @Path("zips/{zip}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getZips(@PathParam("zip") String zip) {
        return Response.ok(ClientBuilder
                .newClient()
                .target("https://service.post.ch/zopa/app/api/addresschecker/v1/")
                .path("zips")
                .queryParam("noCache", System.currentTimeMillis())
                .queryParam("city", "*")
                .queryParam("limit", "60")
                .queryParam("zip", zip + "*")
                .request(MediaType.APPLICATION_JSON)
                .get()
                .getEntity()
        ).build();

        /*
        curl -X GET "https://service.post.ch/zopa/app/api/addresschecker/v1/zips?noCache=1556451724044&city=*&limit=60&zip=1607*"
        {"zips": [
                {"zip":"1607","city18":"Palézieux","city27":"Palézieux"},
                {"zip":"1607","city18":"Palézieux-Village","city27":"Palézieux-Village"},
                {"zip":"1607","city18":"Les Tavernes","city27":"Les Tavernes"},
                {"zip":"1607","city18":"Les Thioleyres","city27":"Les Thioleyres"}
                ],
            "_metadata":{"totalCount":4,"offset":0,"limit":60}}
         */
    }

    @GET
    @Path("houses/{city}/{street}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHouses(@PathParam("city") String city, @PathParam("term") String street) {
        return getHouses(city, street, null);
    }

    @GET
    @Path("houses/{city}/{street}/{houseNbr}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHouses(@PathParam("city") String city, @PathParam("term") String street, @PathParam("houseNbr") String houseNbr) {
        return Response.ok(ClientBuilder
                .newClient()
                .target("https://service.post.ch/zopa/app/api/addresschecker/v1/")
                .path("houses/search")
                .queryParam("noCache", System.currentTimeMillis())
                .queryParam("city", city + "*")
                .queryParam("limit", "60")
                .queryParam("houseNbr", (houseNbr == null ? "" : houseNbr))
                .queryParam("street", street + "*")
                .request(MediaType.APPLICATION_JSON)
                .get()
                .getEntity()
            ).build();

        /*
        curl -X GET "https://service.post.ch/zopa/app/api/addresschecker/v1/houses/search?noCache=1556451675078&city=Pal%C3%A9zieux*&houseNbr=&limit=60&street=marou*"
        {"houses":[
            {"houseNumber":28,"houseKey":76131999,"street":{
                "streetName":"Chemin de la Marouette",
                "zip":{
                    "zip":"1607","city18":"Palézieux","city27":"Palézieux","onrp":790
                },
                "streetValidation":{"isStreetNameValid":true}}},
            ...
        */
    }
}
