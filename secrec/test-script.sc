/*
 * This file is a part of the Sharemind framework.
 * Copyright (C) Cybernetica AS
 *
 * All rights are reserved. Reproduction in whole or part is prohibited
 * without the written consent of the copyright owner. The usage of this
 * code is subject to the appropriate license agreement.
 */
 
import shared3p;
import stdlib;
import table_database;
import shared3p_table_database;
import shared3p_statistics_distribution;

domain pd_shared3p shared3p;

void main() {
    pd_shared3p int32 id = argument("id");
    
    public string ds = "DS1";
    public string table = "ballots";
    
    tdbOpenConnection(ds);
    
    uint64 ballot = tdbVmapNew();

    tdbVmapAddValue(ballot, "values", id);

    if (!tdbTableExists(ds, table)) {
        pd_shared3p int32 nint32;

        // Create a parameter map.
        uint64 params = tdbVmapNew();

        // candidateId
        tdbVmapAddType(params, "types", nint32);
        tdbVmapAddString(params, "names", "vote");

        // Create the table.
        tdbTableCreate(ds, table, params);

        // Clean up the parameters.
        tdbVmapDelete(params);
    }
    
    // Save declaration body.
    tdbInsertRow(ds, table, ballot);
    // Clean up the parameters
    tdbVmapDelete(ballot);
    
    pd_shared3p int32[[1]] votes = tdbReadColumn(ds, table, "vote");
    uint k = size(votes);
    
    pd_shared3p bool[[1]] a1(k);  // votes for canditate 1 (true or false)
    pd_shared3p bool[[1]] a2(k);  // votes for canditate 2 (true or false)
    
    a1 = votes == 1;
    a2 = !a1;
    
    pd_shared3p uint count1 = sum(a1);
    pd_shared3p uint count2 = sum(a2);
    
    pd_shared3p uint[[1]] hist = {count1, count2};
    
    printVector(declassify(hist));
    publish("hist", hist);
}