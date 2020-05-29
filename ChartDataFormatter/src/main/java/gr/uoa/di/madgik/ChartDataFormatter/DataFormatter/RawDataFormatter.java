package gr.uoa.di.madgik.ChartDataFormatter.DataFormatter;

import gr.uoa.di.madgik.ChartDataFormatter.JsonRepresentation.ResponseBody.RawDataJsonResponse;
import gr.uoa.di.madgik.statstool.domain.Result;

import java.util.ArrayList;
import java.util.List;

public class RawDataFormatter extends DataFormatter {

    @Override
    public RawDataJsonResponse toJsonResponse(List<Result> dbAccessResults, Object... args) throws DataFormationException {

        List<List<List<String>>> res = new ArrayList<>();

        for (int i = 0; i < dbAccessResults.size(); i++) {
            Result result = dbAccessResults.get(i);

            res.add(result.getRows());
        }



        return new RawDataJsonResponse(res);
    }
}
