function [ data, passData, resData, opData, payoffData] = readInSecData( i )
%READ_IN_SEC_DATA Reads in screening strategy, and other data to make
%graphs
%Reads in screening strategy, passenger distribution data, resource 
%data and team data to make graphs

    data = csvread(strcat( 'ScreeningStrategy', num2str(i), '.csv'), 1, 3);

    % Read in passenger's category distribution for all flights
    passData = csvread(strcat('PassengerDistribution',num2str(i), '.csv'), 1, 2);
    passData = passData(:,1:end-1);
    
    payoffData = csvread(strcat( 'DefenderPayoffs', num2str(i), '.csv'), 1, 3);
    payoffData = payoffData(1:end-1)
    % Read in resource capacity data
    f = fopen('..\input\ScreeningResources.csv');
    resData = {};
    counter = 0;
    tline = fgetl(f);
    tline = fgetl(f);
    while tline ~= -1
        counter = counter + 1;
        splitted = strsplit( tline, ',');
        capacity = str2double(splitted{2}) .* str2double(splitted{3});
        splitted = { splitted{1}, capacity};
        resData = [resData; (splitted)];
        tline = fgetl(f);
    end
    fclose(f);
    resData(:,3) = num2cell(zeros([size(resData, 1), 1]));
    % Addition
    strcat( '..\ResourceFines', num2str(i), '.csv')
    f = fopen(strcat( '..\ResourceFines', num2str(i), '.csv'));
    tline = fgetl(f);
    tline = fgetl(f);
    while tline ~= -1
        counter = counter + 1;
        splitted =  strsplit( tline, ',');
        res= strtrim(splitted(3));
        resData(find( strncmp(res, resData, length(res) ) ), 3) = num2cell(str2double(splitted(4)));
        tline = fgetl(f);
    end
    
    fclose(f);   
    
    f = fopen('..\input\ScreeningOperations.csv');
    opData = {};
    tline = fgetl(f);
    while tline ~= -1
        counter = counter + 1;
        splitted = strsplit( tline, ',');
        opData = vertcat(opData, {splitted});
        tline = fgetl(f);
    end
    
    fclose(f);
    
end

