/*
 * This file is a part of the Sharemind framework.
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */

// import modules from the secrec standard library
import shared3p;                    // contains secret shared data types and regular functions like sin() and sqrt()
import stdlib;                      // contains standard functions like publish() and print()
import table_database;              // for creating table databases
import shared3p_table_database;     // for inserting and retrieving secret shared values from databases

domain pd_shared3p shared3p;


// cosine function is not declared in the secrec standard library
// so we'll make our own
template<domain D : shared3p>  // this is a C++'like template, D is any protection domain of shared3p kind
D float64[[1]] cos(D float64[[1]] a) {
    float64 piOverTwo = 1.5707963267948966;
    return sin(piOverTwo - a);  // trigonometry functions requiere angles in radians
}


// if it doesn't exist yet, create a table database for location data
void createTable(string datasource, string table) {
    if (!tdbTableExists(datasource, table)) {
        pd_shared3p float64 nfloat64;  // used to indicate type of float64 in params

        // create a vector map for the paramaters (paramater map) 
        // a parameter map is used to create the header of a table
        uint64 params = tdbVmapNew();

        // candidateId
        tdbVmapAddType(params, "types", nfloat64);
        tdbVmapAddString(params, "names", "latitude");

        // vote
        tdbVmapAddType(params, "types", nfloat64);
        tdbVmapAddString(params, "names", "longitude");

        // create the table
        tdbTableCreate(datasource, table, params);

        // the resulting table looks like this:
        //
        //        ---------------------------------------------
        // names: |          "latitude" |         "longitude" |
        // types: | pd_shared3p float64 | pd_shared3p float64 |
        //        ---------------------------------------------
        //        |    coord in radians |    coord in radians |
        //        |                   * |                   * |
        //        |                   * |                   * |
        //        |                   * |                   * |
        //        ---------------------------------------------

        // clean up the parameters
        tdbVmapDelete(params);
    }
}


// add a row to the table database
template<domain D : shared3p>
void storeValue(string datasource, 
                string table, 
                D float64 latitude, 
                D float64 longitude) {

    uint64 params = tdbVmapNew();  // create a vector map containing the data

    // data is added in batches, one batch is one row
    // if you add multiple rows, you need to create a new batch between rows
    // here only one row is added, so creating a new batch is not necessary
    tdbVmapAddValue(params, "values", latitude);
    tdbVmapAddValue(params, "values", longitude);

    tdbInsertRow(datasource, table, params);  // insert the vmap into the table database
}


// calculate the distances as if the earth was flat, this is accurate enough for this application
// https://en.wikipedia.org/wiki/Geographical_distance#Spherical_Earth_projected_to_a_plane
template<domain D : shared3p>
D uint[[1]] calculateDistanceHistogram(string datasource, 
                                     string table,
                                     D float64 lat1,
                                     D float64 long1) {

    // read previously stored location data from the database, store it in two arrays
    pd_shared3p float64[[1]] lat2 = tdbReadColumn(datasource, table, "latitude");
    pd_shared3p float64[[1]] long2 = tdbReadColumn(datasource, table, "longitude");

    uint k = size(lat2);  // how many locations are stored in the database, public value
    float64 R = 6371;  // Earth's mean radius in kilometers

    // the calculations are done on arrays so that all distances can be calculated in parallel
    // this is more efficient then doing it in a for loop

    // calculate the distance between the client's coordinates and all other coordinates
    pd_shared3p float64[[1]] d_lat = lat2 - lat1;
    pd_shared3p float64[[1]] d_long = long2 - long1;

    // declare some arrays to store calculation results
    pd_shared3p float64[[1]] a(k);
    pd_shared3p float64[[1]] b(k);
    pd_shared3p float64[[1]] c(k);
    pd_shared3p float64[[1]] dist(k);

    // calculate the distances with the formula given in the wikipedia article
    a = d_lat * d_lat;
    b = (lat1 + lat2) / 2;
    c = cos(b) * d_long;
    dist = R * sqrt(a + c * c);

    // store boolean arrays of comparisons 
    pd_shared3p bool[[1]] l05 = dist < 0.5;                 // distance less then 0.5 km
    pd_shared3p bool[[1]] l1 = (dist < 1) == (dist > 0.5);  // distance less then 1.0 km
    pd_shared3p bool[[1]] l2 = (dist < 2) == (dist > 1.0);  // distance less then 2.0 km
    pd_shared3p bool[[1]] l5 = (dist < 5) == (dist > 2.0);  // distance less then 5.0 km
    pd_shared3p bool[[1]] m5 = dist > 5;                    // distance greater then 5.0 km

    // sum of a boolean array returns an unsigned integer
    // create an array from the sums of boolean arrays
    return {sum(l05), sum(l1), sum(l2), sum(l5), sum(m5)};
}


// main function executed when the script is called
void main() {
    public string ds = "DS1";  // datasource that is defined in the sharemind configuration
    public string table = "location-data";  // name of the table where the values will be stored

    tdbOpenConnection(ds);

    createTable(ds, table);  // if the table doesn't exist yet, create it 

    // retrieve the client's location data and store it in secret shared double precision floats
    pd_shared3p float64[[1]] location = argument("location");
    pd_shared3p float64 latitude = location[0];
    pd_shared3p float64 longitude = location[1];

    // calculate the distance between the client's location and all locations stored in the database
    // then create a histogram out of it
    pd_shared3p uint[[1]] hist = calculateDistanceHistogram(ds, table, latitude, longitude);

    // publish the histogram so that it can be retrieved by the client
    publish("hist", hist);

    // store the client's location data in the database
    storeValue(ds, table, latitude, longitude);
    tdbCloseConnection(ds);  // close connection to the datasource, just in case it isn't done automatically
}