var libraryType;
var port = "8080";
var domainName = "localhost";
var protocol = "http";

var domainLink = protocol+"://"+domainName+":"+port;

function fetchChart(jsonData){
    $.getJSON(jsonData, handleAdminSideData)
    .done()
    .fail()
    .always();
}

//Function for loading(= appending to the head) a JS file
function loadJS(url, afterLoadCallback){
    
    var fileref=document.createElement('script');
    fileref.onload = afterLoadCallback;
    fileref.setAttribute("type","text/javascript");
    fileref.setAttribute("src", url);
    document.getElementsByTagName('head')[0].appendChild(fileref);

    console.log("Library "+url+" loaded!");
}

//Callback that handles the data sent from the Admin part of the app
function handleAdminSideData(dataJSONobj)
{   
    // dataJSONobj holds the option-ready version of the JSON that will be passed to the Chart library,
    // along with the queries that must be passed to ChartDataizer and eventually to DBAccess
    console.log(dataJSONobj);
    
    var query_chartTypeJson = new Object();
    //Pass the Chart library to ChartDataizer
    query_chartTypeJson.library = dataJSONobj.library;

    //Dynamically add JS library
    if(query_chartTypeJson.library === "Highcharts"){
        
        libraryType = query_chartTypeJson.library;
        //Pass the Chart type to ChartDataizer
        query_chartTypeJson.type = dataJSONobj.chartDescription.chart.type;
        //Pass the Chart data queries to ChartDataizer
        query_chartTypeJson.queries = dataJSONobj.chartDescription.series;

        loadJS("https://code.highcharts.com/6.0/highcharts.js",
         function(){ passToChartDataizer(dataJSONobj,query_chartTypeJson,
                            domainLink+"/chart"); });
    }
}

//Post the admin-side json to the chartDataizer
function passToChartDataizer(dataJSONobj,chartDataizerReadyJSONobj,chartDataizerUrl)
{
    console.log(chartDataizerReadyJSONobj);

    $.ajax(
    {url: this.chartDataizerUrl,
    type: "POST",
    dataType: "json",
    contentType: 'application/json; charset=utf-8',
    data: JSON.stringify(chartDataizerReadyJSONobj),
    cache: false,
    success: function(data){ handleChartDataizerResponse(data,dataJSONobj) }            
    })
    .done()
    .fail()
    .always();
}

function handleChartDataizerResponse(responseData, originalDataJSONobj)
{   
    console.log(responseData);
    
    //Hide children elements of container
    $("#container").children().remove();
    
    if(libraryType === "Highcharts"){
        var chartJson = convertToValidHighchartJson(responseData, originalDataJSONobj);
        console.log(chartJson);
        Highcharts.chart('container',chartJson);
    }

}

function convertToValidHighchartJson(responseData, originJson){

    var convertedJson = originJson.chartDescription;
    if(Object.keys(convertedJson.series).length != Object.keys(responseData.series).length)
        return null;
    
    for (let index = 0; index < Object.keys(convertedJson.series).length; index++){
        convertedJson.series[index].data = responseData.series[index].data;
        convertedJson.series[index].query = null;
    } 

    return convertedJson;
}