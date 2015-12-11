% TO DO: Try example with mult timewindows
close all;
clear all;

overflowByResource = [];
overflowFines = [];
overflowPayoff = [];

for i=0:50
    [ data, passData, resData, opData, payoffData] = readInSecData(i);
    
    % Divide by six because there are six categories.
    numFlights = size(data,1)/6;
    
    % Tells us how many operations there are
    numTeams = size(data,2);
    
    % Data is reshaped to be risk categories, flights, operations (teams)
    data = reshape(data, [6, numFlights, numTeams]);

    
    for k = 1:numFlights
        for j = 1:6
            data(j, k,:) = data(j, k,:).*passData(k,j);
        end
    end
    operations = squeeze(sum(sum(data, 2),1));
    
    opsbyflight = squeeze(sum(data,1));
    
    s = squeeze((sum(data, 2)));
    oprep = repmat( operations, 1, size(s, 1))';
    s = s./ oprep;
    % figure
    % bar3(s)
    cats = {'SELECTEE', 'UNKNOWN', 'LOWRISK1', 'LOWRISK2', 'LOWRISK3', 'LOWRISK4'};
    % set(gca,'YTickLabel', cats)
    ops = [];
    for opnum = 1: numel(opData)
        ops = [ops, {strcat('OP', num2str(opnum))}];
    end
    % ops = {'OP1', 'OP2', 'OP3', 'OP4', 'OP5', 'OP6', 'OP7', '
    % set(gca,'XTickLabel', ops)
    % title('Percentage of people going down each line')

    % data -- data(risk category, flight, operation)

    resOver = zeros( [size(resData, 1), 1] );
    resOverByFlight = zeros( [size(resData, 1), numFlights ] );
    for numTeams = 1: size(opData, 1)
        currOp = opData{numTeams}
        for opr = 1:numel(currOp)
            currR = currOp(opr);
            currR = strrep( currR, ' ', '');
            for numRes = 1: numel(resData)/3
                r = resData(numRes,1);
                if strcmp(r{1}, currR{1})
                    resOver(numRes) = resOver(numRes) + operations(numTeams);
                    resOverByFlight(numRes, :) = resOverByFlight(numRes, :)' + opsbyflight(:, numTeams);
                end
            end
        end
    end

    
    
    
    % hold off;
    % figure;
    % bar(resOver)
% 
    labels = {};
    caps = [];
    for lnum = 1: numel(resData) ./ 3
        labels = [labels; resData{lnum, 1}];
        caps = [caps; resData{lnum, 2}];
    end

    for lnum = 1: numel(resData) / 3
        subplot(2,3,lnum)
        plot(resOverByFlight(1:30), 'r')
        hold on;
        plot(resOverByFlight(30:60), 'g')
        hold on;
        plot(resOverByFlight(60:90), 'b')
        title( labels{lnum} )
        xlabel( 'Flight #' )
        ylabel( 'Percentage of capacity used' )
    end
    
    
%     set(gca, 'XTickLabel', labels);
%     title('Total Number of Screenees by Resource')
%     hold off;
%     figure;

    percentages = resOver ./caps

%     for opnum = 1:size(opsbyflight, 2)
%         figure;
%         plot( opsbyflight(:,opnum));
%     end
    
%     bar(percentages);
%     set(gca, 'XTickLabel', labels);
%     title('Total Percentage of Resources Used');
    % figure;
    % Number of people sent down each team
    % eachLane = squeeze(sum(sum(data, 1), 2));
    % bar( eachLane );
    % set(gca, 'XTickLabel', ops);
    % title( 'Number of people to each team' );
    
%     overflowByResource1 = [overflowByResource1
%     overflowByResource2 = [overflowByResource2 
%     overflowByResource3 = [overflowByResource3
    
    overflowByResource = [overflowByResource percentages];
    overflowFines = [overflowFines cell2mat( resData(:,3)) ];
    overflowPayoff = [overflowPayoff payoffData];
end

for lnum = 1: numel(resData) / 3
   subplot(2,3,lnum)
   plot(overflowFines(lnum,:), overflowByResource(lnum,:))
   title( labels{lnum} )
   xlabel( 'Fine' )
   ylabel( 'Percentage of capacity used' )
end

% add risk categories here

figure;
plot(overflowFines(lnum,:), overflowPayoff');
close all;