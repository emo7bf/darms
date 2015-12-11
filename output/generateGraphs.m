% TO DO: Try example with mult timewindows
close all;
clear all;
numWindows = 3;
overflowByResource = [];
overflowFines = [];
overflowPayoff = [];

for i=0:50
    [ data, passData, resData, opData, payoffData ] = readInSecData(i);
    
    % Divide by six because there are six categories.
    numFlights = size(data,1)/6;
    
    % Tells us how many operations there are
    numTeams = size(data,2);
    
    % Data is reshaped to be risk categories, flights, operations (teams)
    data = reshape(data, [6, numFlights, numTeams]);
    
    data1 = [];

    data1 = data(:,1:90,:);
    data2 = data(:,91:120,:);
    data3 = data(:,121:end,:);
    
    data = cat( 2, data1, data2, data3 );
    
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
        % subplot(2,3,lnum)
        plot(1:90, resOverByFlight(lnum, 1:90), 'r')
        hold on;
        plot(90:150, resOverByFlight(lnum, 90:150) , 'g')
        hold on;
        plot(150:169, resOverByFlight(lnum, 150:end) , 'b')
        title( labels{lnum} )
        xlabel( 'Flight #' )
        ylabel( 'Number of Screenees' )
        hold off;
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
   % subplot(2,3,lnum)
   figure;
   plot(overflowFines(lnum,:), overflowByResource(lnum,:)*caps(lnum) )
   h = refline([0, caps(lnum)])
   h.Color = 'r'
   title( strcat( labels{lnum},' Amount of Overflow'),'FontSize',15  )
   xlabel( '\it{f_{ETD}}','FontSize',20 )
   ylabel( 'Number of Screenees', 'FontSize',20 )
end

% add risk categories here

figure;
plot(overflowFines(lnum,:), overflowPayoff');

figure;
passDist = sum(passData, 1);
normPassDist = passDist / norm( passDist );
normPassRep = repmat( normPassDist, size(overflowFines,2), 1 );

totalPayoff = overflowPayoff' .* normPassRep;

plot(overflowFines(lnum,:), sum( overflowPayoff', 2) );
h1 = xlabel( '\it{f_{r \in R}}', 'Interpreter','LaTex','FontSize',20);
h2 = ylabel( 'Utility', 'Interpreter','LaTex','FontSize',20 );
set([h1 h2], 'interpreter', 'tex');
title( 'Total Defender Utility','FontSize',20)
close all;